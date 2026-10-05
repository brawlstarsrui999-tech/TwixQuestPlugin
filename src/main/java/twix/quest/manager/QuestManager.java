package twix.quest.manager;

import net.kyori.adventure.title.Title;
import net.milkbowl.vault.economy.Economy;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import twix.quest.TwixQuestPlugin;
import twix.quest.data.PlayerQuestData;
import twix.quest.data.QuestRegistry;
import twix.quest.quest.QuestDefinition;
import twix.quest.quest.QuestType;
import twix.quest.reward.Reward;
import twix.quest.reward.RewardItem;
import twix.quest.util.TextUtil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Контролирует прогресс игроков, событийные хуки и выдачу наград.
 *
 * <p>Квесты проходятся строго по очереди: засчитывается только первый
 * невыполненный. Прогресс хранится в файле игрока (см. {@link PlayerQuestData}),
 * поэтому не теряется при выходе и перезапуске сервера.</p>
 */
public final class QuestManager {

    /** Ключ обычного счётчика (убийства, продажи, крафты...). Для вложений ключ — имя материала. */
    private static final String COUNTER = "n";

    /** Сколько тиков должна наиграть жертва, чтобы убийство засчиталось в квесте на PvP (30 минут). */
    private static final int MIN_VICTIM_PLAYTIME_TICKS = 20 * 60 * 30;

    /** Чем закончился клик «вложить». */
    public enum DepositStatus {
        /** Квест не текущий. */
        NOT_ACTIVE,
        /** У квеста нет предметов, которые можно вложить. */
        NOT_CLAIMABLE,
        /** Нужных предметов в инвентаре нет. */
        NOTHING,
        /** Часть или все предметы вложены. */
        DEPOSITED,
        /** Предмет предъявлен без изъятия (квесты «скрафтить»). */
        CLAIMED,
        /** «Предъявить» нечего: нужного предмета нет. */
        MISSING
    }

    /** Итог вложения: статус, сколько чего забрано, закрыт ли квест. */
    public record DepositResult(DepositStatus status, Map<Material, Integer> taken, boolean completed) {
        static DepositResult of(DepositStatus s) {
            return new DepositResult(s, Map.of(), false);
        }

        public int totalTaken() {
            int n = 0;
            for (int v : taken.values()) n += v;
            return n;
        }
    }

    private final JavaPlugin plugin;
    private final QuestRegistry registry;
    private final PlayerQuestData data;

    public QuestManager(JavaPlugin plugin, QuestRegistry registry, PlayerQuestData data) {
        this.plugin = plugin;
        this.registry = registry;
        this.data = data;
    }

    // ------------------------------------------------------------------
    // Текущий квест и прогресс
    // ------------------------------------------------------------------

    public QuestDefinition currentQuest(UUID uuid) {
        for (QuestDefinition q : registry.all()) {
            if (!data.isCompleted(uuid, q.id)) return q;
        }
        return null;
    }

    /** Сколько «единиц» квеста уже сделано (для вложений — по всем материалам вместе). */
    public int currentProgress(UUID uuid, QuestDefinition q) {
        if (q.type == QuestType.DEPOSIT_ITEM) {
            int sum = 0;
            for (QuestDefinition.MaterialRequirement r : q.requirements()) {
                sum += Math.min(r.amount, deposited(uuid, q, r.material));
            }
            return sum;
        }
        if (q.type == QuestType.REACH_BALANCE) {
            Player p = Bukkit.getPlayer(uuid);
            Economy eco = TwixQuestPlugin.inst().getVaultEconomy();
            if (p == null || eco == null) return 0;
            return (int) Math.max(0, Math.min(q.amount, Math.floor(eco.getBalance(p))));
        }
        return Math.min(q.amount, data.getCounter(uuid, q.id, COUNTER));
    }

    /** Сколько штук материала уже вложено в квест. */
    public int deposited(UUID uuid, QuestDefinition q, Material m) {
        return data.getCounter(uuid, q.id, m.name());
    }

    private void addProgress(UUID uuid, QuestDefinition q, int by) {
        if (by <= 0 || data.isCompleted(uuid, q.id)) return;
        int now = Math.min(q.amount, data.getCounter(uuid, q.id, COUNTER) + by);
        data.setCounter(uuid, q.id, COUNTER, now);
        if (now >= q.amount) {
            completeQuest(uuid, q);
            return;
        }
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            p.sendActionBar(TextUtil.mm("<#9B59FF>Квест #" + q.id + " <dark_gray>· <#D3A8FF>" + q.name
                    + " <dark_gray>· <#F1C40F>" + now + "<gray>/<#F1C40F>" + q.amount));
        }
    }

    public boolean manualProgress(UUID uuid, int by) {
        QuestDefinition q = currentQuest(uuid);
        if (q == null || q.type == QuestType.DEPOSIT_ITEM) return false;
        addProgress(uuid, q, by);
        return true;
    }

    public boolean forceCompleteCurrent(UUID uuid) {
        QuestDefinition q = currentQuest(uuid);
        if (q == null) return false;
        completeQuest(uuid, q);
        return true;
    }

    // ------------------------------------------------------------------
    // Вложение предметов (клик по квесту)
    // ------------------------------------------------------------------

    /**
     * Клик «вложить» на текущем квесте. Для вложений ({@code consume: true}) забирает
     * из инвентаря ровно столько, сколько ещё нужно (или сколько есть — можно вкладывать
     * частями), и сразу сохраняет прогресс. Для «скрафтить» только проверяет, что предмет
     * есть, и ничего не забирает.
     */
    public DepositResult deposit(Player p, QuestDefinition q) {
        UUID id = p.getUniqueId();
        QuestDefinition active = currentQuest(id);
        if (active == null || active.id != q.id) return DepositResult.of(DepositStatus.NOT_ACTIVE);
        if (!q.isClaimable()) return DepositResult.of(DepositStatus.NOT_CLAIMABLE);

        if (!q.consume) {
            return claimWithoutConsuming(p, q);
        }

        Map<Material, Integer> taken = new LinkedHashMap<>();
        for (QuestDefinition.MaterialRequirement r : q.requirements()) {
            int have = deposited(id, q, r.material);
            int need = r.amount - have;
            if (need <= 0) continue;
            int took = take(p, r.material, need);
            if (took <= 0) continue;
            data.setCounter(id, q.id, r.material.name(), have + took);
            taken.put(r.material, took);
        }

        if (taken.isEmpty()) {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            TextUtil.send(p, "<red>В инвентаре нет нужных предметов.<reset> <gray>Осталось вложить:");
            for (String line : remainingLines(id, q)) TextUtil.send(p, line);
            return DepositResult.of(DepositStatus.NOTHING);
        }

        // Предметы уже изъяты — прогресс обязан оказаться на диске немедленно.
        data.save(id);
        p.updateInventory();

        StringBuilder sb = new StringBuilder("<#27AE60>✔ Вложено: ");
        boolean first = true;
        for (Map.Entry<Material, Integer> e : taken.entrySet()) {
            if (!first) sb.append("<dark_gray>, ");
            sb.append("<white>").append(e.getValue()).append("<gray>× <#E6E6FA>").append(TextUtil.itemName(e.getKey()));
            first = false;
        }
        TextUtil.send(p, sb.toString());
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);

        boolean done = isSatisfied(id, q);
        if (done) {
            completeQuest(id, q);
        } else {
            TextUtil.send(p, "<gray>Осталось вложить:");
            for (String line : remainingLines(id, q)) TextUtil.send(p, line);
        }
        return new DepositResult(DepositStatus.DEPOSITED, taken, done);
    }

    private DepositResult claimWithoutConsuming(Player p, QuestDefinition q) {
        UUID id = p.getUniqueId();
        for (QuestDefinition.MaterialRequirement r : q.requirements()) {
            if (countInInventory(p, r.material, false) < r.amount) {
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
                TextUtil.send(p, "<red>Сначала скрафтите: <#E6E6FA>" + TextUtil.itemName(r.material)
                        + (r.amount > 1 ? " <white>×" + r.amount : "")
                        + "<reset> <gray>(предмет засчитается сам, а если он уже у вас — нажмите на квест ещё раз).");
                return DepositResult.of(DepositStatus.MISSING);
            }
        }
        data.setCounter(id, q.id, COUNTER, q.amount);
        completeQuest(id, q);
        return new DepositResult(DepositStatus.CLAIMED, Map.of(), true);
    }

    /** Всё ли уже вложено. */
    public boolean isSatisfied(UUID id, QuestDefinition q) {
        for (QuestDefinition.MaterialRequirement r : q.requirements()) {
            if (deposited(id, q, r.material) < r.amount) return false;
        }
        return true;
    }

    /** Строки «▸ Название x/y» только по ещё не закрытым требованиям. */
    public java.util.List<String> remainingLines(UUID id, QuestDefinition q) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (QuestDefinition.MaterialRequirement r : q.requirements()) {
            int have = Math.min(r.amount, deposited(id, q, r.material));
            if (have >= r.amount) continue;
            out.add("<#BB8CFF>  ▸ <#E6E6FA>" + TextUtil.itemName(r.material)
                    + " <gray>" + have + "/<#F1C40F>" + r.amount);
        }
        return out;
    }

    /**
     * Сколько штук материала лежит в инвентаре игрока (хотбар + основной инвентарь).
     *
     * @param plainOnly считать только «чистые» предметы — без имён, чар и чужих данных.
     *                  Именно такие и забираются при вложении: особые предметы (например,
     *                  переименованные или с метаданными другого плагина) квест не съест.
     */
    public int countInInventory(Player p, Material m, boolean plainOnly) {
        int total = 0;
        ItemStack proto = plainOnly ? new ItemStack(m) : null;
        for (ItemStack s : p.getInventory().getStorageContents()) {
            if (s == null || s.getType() != m) continue;
            if (plainOnly && !s.isSimilar(proto)) continue;
            total += s.getAmount();
        }
        return total;
    }

    /** Забирает до {@code max} «чистых» предметов; возвращает, сколько реально забрано. */
    private int take(Player p, Material m, int max) {
        if (max <= 0) return 0;
        int want = Math.min(countInInventory(p, m, true), max);
        if (want <= 0) return 0;
        Map<Integer, ItemStack> notRemoved = p.getInventory().removeItem(new ItemStack(m, want));
        int left = 0;
        for (ItemStack s : notRemoved.values()) left += s.getAmount();
        return want - left;
    }

    // ------------------------------------------------------------------
    // Завершение квеста и награды
    // ------------------------------------------------------------------

    public void completeQuest(UUID uuid, QuestDefinition q) {
        if (data.isCompleted(uuid, q.id)) return;
        Player p = Bukkit.getPlayer(uuid);
        if (p == null) return; // награду некому выдать — квест не закрываем
        data.setCompleted(uuid, q.id);
        data.clearProgress(uuid, q.id);
        data.setLastKnownName(p); // заодно сохраняет файл

        if (!q.silent) {
            TextUtil.broadcast("<#D3A8FF>Игрок <#BB8CFF><b>" + TextUtil.escapeMiniMessage(p.getName())
                    + "</b> выполнил квест <#9B59FF><b>#" + q.id + " " + q.name + "</b>!");
        }
        p.showTitle(Title.title(TextUtil.mm("<#27AE60><bold>Квест выполнен!"),
                TextUtil.mm("<#D3A8FF>#" + q.id + " " + q.name)));
        TextUtil.send(p, "<#F1C40F><bold>Награда:</bold> " + q.reward.inline());

        giveReward(p, q.reward);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);

        QuestDefinition next = currentQuest(uuid);
        if (next != null) {
            TextUtil.send(p, "<#9B59FF><b>Следующий квест:</b> <#D3A8FF>#" + next.id + " " + next.name);
            TextUtil.send(p, "<gray>" + next.description);
        } else {
            TextUtil.send(p, "<#9B59FF><b>Все квесты пройдены!</b> <#D3A8FF>Поздравляем!");
        }
    }

    /** Выдача награды игроку. */
    public void giveReward(Player p, Reward reward) {
        TwixQuestPlugin tp = TwixQuestPlugin.inst();
        boolean dropped = false;
        for (RewardItem item : reward.items) {
            for (ItemStack stack : item.buildStacks()) {
                for (ItemStack rest : p.getInventory().addItem(stack).values()) {
                    // Не влезло — кладём под ноги, но поднять сможет только получатель награды.
                    Item drop = p.getWorld().dropItem(p.getLocation(), rest);
                    drop.setOwner(p.getUniqueId());
                    dropped = true;
                }
            }
        }
        if (dropped) {
            TextUtil.send(p, "<#E67E22>Инвентарь полон — часть награды лежит рядом с вами (подобрать её можете только вы).");
        }
        if (reward.vaultCoins > 0) {
            Economy eco = tp.getVaultEconomy();
            if (eco != null) {
                eco.depositPlayer(p, reward.vaultCoins);
            } else {
                TextUtil.send(p, "<red>Награда монетами пропущена: Vault не найден.");
            }
        }
        if (reward.twixCoins > 0) {
            PlayerPointsAPI pp = tp.getPlayerPointsAPI();
            if (pp != null) {
                pp.give(p.getUniqueId(), reward.twixCoins);
            } else {
                TextUtil.send(p, "<red>Twixcoin не получены: PlayerPoints не найден.");
            }
        }
        if (reward.expLevels > 0) {
            p.giveExpLevels(reward.expLevels);
        }
        for (String c : reward.commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.replace("%player%", p.getName()));
        }
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
    }

    // ------------------------------------------------------------------
    // События
    // ------------------------------------------------------------------

    public void onMineBlock(BlockBreakEvent e, Material material) {
        Player p = e.getPlayer();
        QuestDefinition q = currentQuest(p.getUniqueId());
        if (q == null || q.type != QuestType.MINE_BLOCK) return;
        if (q.itemMaterial == null || !q.itemMaterial.equals(material)) return;
        addProgress(p.getUniqueId(), q, 1);
    }

    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack result = e.getInventory().getResult();
        if (result == null) return;
        QuestDefinition q = currentQuest(p.getUniqueId());
        if (q == null || q.type != QuestType.CRAFT_ITEM || q.itemMaterial == null) return;
        if (q.consume) return; // здесь предмет нужно сдать кликом по квесту
        if (result.getType() == q.itemMaterial) addProgress(p.getUniqueId(), q, Math.max(1, result.getAmount()));
    }

    /**
     * Сделка с байером (BaerPlugin / BuyerPlugin).
     *
     * @param sold   материалы, которых у игрока стало МЕНЬШЕ, — то есть проданные байеру
     * @param bought материалы, которых стало БОЛЬШЕ, — то есть купленные у байера
     */
    public void onBuyerTrade(Player p, Map<Material, Integer> sold, Map<Material, Integer> bought) {
        if (p == null) return;
        QuestDefinition q = currentQuest(p.getUniqueId());
        if (q == null) return;

        int gained;
        if (q.type == QuestType.SELL_TO_BUYER) {
            gained = matchAmount(sold, q);
            if (q.countBuys) gained += matchAmount(bought, q);
        } else if (q.type == QuestType.BUY_FROM_BUYER) {
            gained = matchAmount(bought, q);
            if (q.countBuys) gained += matchAmount(sold, q);
        } else {
            return;
        }
        if (gained > 0) addProgress(p.getUniqueId(), q, gained);
    }

    /**
     * Сколько единиц из сделки подходит под квест.
     * Если у квеста не указан {@code material} — засчитывается ЛЮБОЙ предмет
     * (квесты вида «продайте что-нибудь байеру»).
     */
    private static int matchAmount(Map<Material, Integer> items, QuestDefinition q) {
        if (items == null || items.isEmpty()) return 0;
        if (q.itemMaterial == null) {
            int sum = 0;
            for (int v : items.values()) sum += Math.max(0, v);
            return sum;
        }
        return Math.max(0, items.getOrDefault(q.itemMaterial, 0));
    }

    /** Совместимость со старым API и с прямым мостом из байер-плагина. */
    public void onSellToBuyer(Player p, ItemStack stack) {
        if (p == null || stack == null || stack.getType() == Material.AIR) return;
        onBuyerTrade(p, Map.of(stack.getType(), stack.getAmount()), Map.of());
    }

    public void onEntityKilled(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        QuestDefinition q = currentQuest(killer.getUniqueId());
        if (q == null) return;
        switch (q.type) {
            case KILL_MOB -> {
                if (q.entityType != null && q.entityType == e.getEntityType()) {
                    addProgress(killer.getUniqueId(), q, 1);
                }
            }
            case KILL_PLAYER -> {
                if (!(e.getEntity() instanceof Player victim)) return;
                if (victim.getUniqueId().equals(killer.getUniqueId())) return;
                // Жертва должна наиграть минимум 30 минут (общее игровое время в тиках).
                if (victim.getStatistic(Statistic.PLAY_ONE_MINUTE) >= MIN_VICTIM_PLAYTIME_TICKS) {
                    addProgress(killer.getUniqueId(), q, 1);
                } else {
                    TextUtil.send(killer, "<red>Этот игрок ещё не наиграл 30 минут — убийство не засчитано.");
                }
            }
            default -> { }
        }
    }

    /**
     * Вход в мир. Работает через событие смены мира, а НЕ через достижение «Мы должны
     * пойти глубже»: так квест проходят и те, у кого достижение уже получено.
     */
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        QuestDefinition q = currentQuest(p.getUniqueId());
        if (q == null || q.type != QuestType.ENTER_WORLD) return;
        if (worldMatches(q, p.getWorld())) addProgress(p.getUniqueId(), q, 1);
    }

    private static boolean worldMatches(QuestDefinition q, World w) {
        if (q.worldName == null && q.worldEnv == null) return false;
        if (q.worldName != null && !q.worldName.equalsIgnoreCase(w.getName())) return false;
        return q.worldEnv == null || q.worldEnv == w.getEnvironment();
    }

    public void onStriderMount(Player p) {
        QuestDefinition q = currentQuest(p.getUniqueId());
        if (q == null || q.type != QuestType.MOUNT_STRIDER) return;
        addProgress(p.getUniqueId(), q, 1);
    }

    public void onTradeClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getInventory().getType() != InventoryType.MERCHANT) return;
        if (e.getRawSlot() != 2) return;
        if (e.getAction() == InventoryAction.NOTHING) return; // клик по результату, но сделки не произошло
        ItemStack result = e.getCurrentItem();
        if (result == null || result.getType() == Material.AIR) return;
        QuestDefinition q = currentQuest(p.getUniqueId());
        if (q != null && q.type == QuestType.TRADE_VILLAGER) {
            addProgress(p.getUniqueId(), q, 1);
        }
    }

    /**
     * Периодическая проверка состояний, у которых нет собственного события
     * (вызывается раз в пару секунд для каждого игрока):
     * <ul>
     *   <li>баланс — монеты могли прийти откуда угодно;</li>
     *   <li>«зайти в мир» — игрок уже может стоять в нужном мире, когда квест открылся;</li>
     *   <li>«попасть в структуру» — дешёвая проверка «стою ли я внутри», вместо поиска
     *       ближайшей структуры при каждом шаге (он замораживал сервер).</li>
     * </ul>
     */
    public void poll(Player p) {
        QuestDefinition q = currentQuest(p.getUniqueId());
        if (q == null) return;
        switch (q.type) {
            case REACH_BALANCE -> checkBalance(p, q);
            case ENTER_WORLD -> {
                if (worldMatches(q, p.getWorld())) addProgress(p.getUniqueId(), q, q.amount);
            }
            case VISIT_STRUCTURE -> checkStructure(p, q);
            default -> { }
        }
    }

    private void checkBalance(Player p, QuestDefinition q) {
        Economy eco = TwixQuestPlugin.inst().getVaultEconomy();
        if (eco == null) return;
        if (eco.getBalance(p) >= q.amount) addProgress(p.getUniqueId(), q, q.amount);
    }

    private void checkStructure(Player p, QuestDefinition q) {
        if (q.structure == null) return;
        World w = p.getWorld();
        if (q.worldEnv != null && w.getEnvironment() != q.worldEnv) return;
        // hasStructureAt — это «лежит ли точка внутри частей структуры»: проверка мгновенная,
        // в отличие от locateNearestStructure, который ищет по огромному радиусу.
        if (w.hasStructureAt(p.getLocation(), q.structure)) {
            addProgress(p.getUniqueId(), q, q.amount);
        }
    }

    public void onQuit(PlayerQuitEvent e) {
        data.save(e.getPlayer().getUniqueId());
    }
}
