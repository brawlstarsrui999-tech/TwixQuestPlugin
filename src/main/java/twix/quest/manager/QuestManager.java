package twix.quest.manager;

import net.milkbowl.vault.economy.Economy;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import twix.quest.TwixQuestPlugin;
import twix.quest.data.PlayerQuestData;
import twix.quest.data.QuestRegistry;
import twix.quest.quest.QuestDefinition;
import twix.quest.quest.QuestType;
import twix.quest.reward.Reward;
import twix.quest.util.TextUtil;

import java.util.*;

/** Контролирует прогресс игроков, событийные хуки и выдачу наград. */
public final class QuestManager {

    private final JavaPlugin plugin;
    private final QuestRegistry registry;
    private final PlayerQuestData data;

    /** Прогресс текущего квеста: uuid -> id квеста -> уже сделано (int). */
    private final Map<UUID, Map<Integer, Integer>> progress = new HashMap<>();

    private final Map<UUID, Long> lastProgressPing = new HashMap<>();

    public QuestManager(JavaPlugin plugin, QuestRegistry registry, PlayerQuestData data) {
        this.plugin = plugin;
        this.registry = registry;
        this.data = data;
    }

    public QuestDefinition currentQuest(UUID uuid) {
        for (QuestDefinition q : registry.all()) {
            if (!data.isCompleted(uuid, q.id)) return q;
        }
        return null;
    }

    public int currentProgress(UUID uuid, QuestDefinition q) {
        return progress.computeIfAbsent(uuid, k -> new HashMap<>()).getOrDefault(q.id, 0);
    }

    private void addProgress(UUID uuid, QuestDefinition q, int by) {
        Map<Integer, Integer> map = progress.computeIfAbsent(uuid, k -> new HashMap<>());
        map.put(q.id, Math.min(q.amount, map.getOrDefault(q.id, 0) + by));
        ping(uuid, q);
    }

    public boolean manualProgress(UUID uuid, int by) {
        QuestDefinition q = currentQuest(uuid);
        if (q == null) return false;
        addProgress(uuid, q, by);
        return true;
    }

    public boolean forceCompleteCurrent(UUID uuid) {
        QuestDefinition q = currentQuest(uuid);
        if (q == null) return false;
        addProgress(uuid, q, q.amount);
        return true;
    }

    private void ping(UUID uuid, QuestDefinition q) {
        int now = currentProgress(uuid, q);
        long last = lastProgressPing.getOrDefault(uuid, 0L);
        if (System.currentTimeMillis() - last > 700L) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                TextUtil.send(p, "<gray>Прогресс квеста <color:#9B59FF>#" + q.id + " " + q.name + "</color>: <aqua>"
                        + now + "/" + q.amount);
                lastProgressPing.put(uuid, System.currentTimeMillis());
            }
        }
        if (now >= q.amount) {
            completeQuest(uuid, q);
        }
    }

    public void completeQuest(UUID uuid, QuestDefinition q) {
        if (data.isCompleted(uuid, q.id)) return;
        data.setCompleted(uuid, q.id);
        Player p = Bukkit.getPlayer(uuid);
        if (p == null) return;

        if (!q.silent) {
            TextUtil.broadcast("<#D3A8FF>Игрок <color:#BB8CFF><b>" + p.getName() + "</b></color> выполнил квест <color:#9B59FF><b>#" + q.id + " " + q.name + "</b></color>!");
            String rewardLine = q.reward.describe();
            TextUtil.send(p, "<gold>Награда: <color:#F1C40F>" + rewardLine + "</gold>");
        }

        giveReward(p, q.reward);

        QuestDefinition next = currentQuest(uuid);
        if (next != null) {
            TextUtil.send(p, "<color:#9B59FF><b>Следующий квест:</b> <color:#D3A8FF>#" + next.id + " " + next.name);
            TextUtil.send(p, "<gray>" + next.description);
        } else {
            TextUtil.send(p, "<color:#9B59FF><b>Все квесты пройдены!</b> <color:#D3A8FF>Поздравляем!");
        }
        progress.getOrDefault(uuid, Collections.emptyMap()).remove(q.id);
        data.setLastKnownName(p);
    }

    /** Выдача награды игроку. */
    public void giveReward(Player p, Reward reward) {
        TwixQuestPlugin tp = TwixQuestPlugin.inst();
        if (reward.items != null) {
            for (var item : reward.items) {
                ItemStack stack = item.build();
                HashMap<Integer, ItemStack> left = p.getInventory().addItem(stack);
                for (ItemStack s : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), s);
            }
        }
        if (reward.vaultCoins > 0) {
            Economy eco = tp.getVaultEconomy();
            if (eco != null) {
                eco.depositPlayer(p, reward.vaultCoins);
            } else {
                p.sendMessage(ChatColor.RED + "Награда монетами пропущена: Vault не найден.");
            }
        }
        if (reward.twixCoins > 0) {
            PlayerPointsAPI pp = tp.getPlayerPointsAPI();
            if (pp != null) {
                pp.give(p.getUniqueId(), reward.twixCoins);
            } else {
                p.sendMessage(ChatColor.RED + "Twixcoin не получены: PlayerPoints не найден.");
            }
        }
        if (reward.expLevels > 0) {
            p.giveExpLevels(reward.expLevels);
        }
        if (reward.commands != null) {
            for (String c : reward.commands) {
                String parsed = c.replace("%player%", p.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            }
        }
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
    }

    // --- События ---

    public void onMineBlock(BlockBreakEvent e, Material material) {
        Player p = e.getPlayer();
        QuestDefinition q = currentQuest(p.getUniqueId());
        if (q == null) return;
        if (q.type != QuestType.MINE_BLOCK) return;
        if (q.itemMaterial == null || !q.itemMaterial.equals(material)) return;
        addProgress(p.getUniqueId(), q, 1);
    }

    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack result = e.getInventory().getResult();
        if (result == null) return;
        QuestDefinition q = currentQuest(p.getUniqueId());
        if (q == null || q.type != QuestType.CRAFT_ITEM || q.itemMaterial == null) return;
        if (result.getType() == q.itemMaterial) addProgress(p.getUniqueId(), q, result.getAmount());
    }

    public void onSellToBuyer(Player p, ItemStack stack) {
        QuestDefinition q = currentQuest(p.getUniqueId());
        if (q == null || q.type != QuestType.SELL_TO_BUYER || q.itemMaterial == null) return;
        if (stack != null && q.itemMaterial.equals(stack.getType())) {
            addProgress(p.getUniqueId(), q, stack.getAmount());
        }
    }

    public void onEntityKilled(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        QuestDefinition q = currentQuest(killer.getUniqueId());
        if (q == null) return;
        switch (q.type) {
            case KILL_MOB -> {
                if (q.entityType == null && q.itemMaterial == null) return;
                if (q.entityType == e.getEntityType()) {
                    addProgress(killer.getUniqueId(), q, 1);
                }
            }
            case KILL_PLAYER -> {
                if (!e.getEntityType().equals(EntityType.PLAYER)) return;
                // Проверим, что жертва играла >= 30 минут.
                // getOfflinePlayerIfCached принимает только String — используем прямую загрузку по UUID.
                OfflinePlayer victim = Bukkit.getOfflinePlayer(e.getEntity().getUniqueId());
                boolean eligible = false;
                if (victim != null) {
                    // Используем PLAY_ONE_MINUTE жертвы (тики)
                    int ticks = victim.getStatistic(Statistic.PLAY_ONE_MINUTE);
                    eligible = ticks >= 20 * 60 * 30; // 30 минут
                }
                if (eligible) {
                    addProgress(killer.getUniqueId(), q, 1);
                } else {
                    TextUtil.send(killer, "<red>Этот игрок ещё не наиграл 30 минут.");
                }
            }
            default -> {}
        }
    }

    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        World.Environment now = p.getWorld().getEnvironment();
        String newName = p.getWorld().getName();
        QuestDefinition q = currentQuest(p.getUniqueId());
        if (q == null) return;
        if (q.type != QuestType.ENTER_WORLD) return;
        if (q.worldName != null && !q.worldName.equalsIgnoreCase(newName)) return;
        if (q.worldEnv != null && q.worldEnv != now) return;
        if (q.worldName == null && q.worldEnv == null) return;
        addProgress(p.getUniqueId(), q, 1);
    }

    public void onStriderMount(Player p) {
        QuestDefinition q = currentQuest(p.getUniqueId());
        if (q == null) return;
        if (q.type != QuestType.MOUNT_STRIDER) return;
        if (p.getWorld().getEnvironment() != World.Environment.NETHER) return;
        addProgress(p.getUniqueId(), q, 1);
    }

    public void onTradeClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getInventory().getType() != InventoryType.MERCHANT) return;
        if (e.getRawSlot() != 2) return;
        ItemStack result = e.getCurrentItem();
        if (result == null || result.getType() == Material.AIR) return;
        QuestDefinition q = currentQuest(p.getUniqueId());
        if (q != null && q.type == QuestType.TRADE_VILLAGER) {
            addProgress(p.getUniqueId(), q, 1);
        }
    }

    public void checkBalance(Player p) {
        QuestDefinition q = currentQuest(p.getUniqueId());
        if (q == null || q.type != QuestType.REACH_BALANCE) return;
        Economy eco = TwixQuestPlugin.inst().getVaultEconomy();
        if (eco == null) return;
        double balance = eco.getBalance(p);
        if (balance >= q.amount) addProgress(p.getUniqueId(), q, q.amount);
    }

    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        QuestDefinition q = currentQuest(p.getUniqueId());
        if (q == null || q.type != QuestType.VISIT_STRUCTURE) return;
        if (q.structure == null) return;
        org.bukkit.Location here = p.getLocation();
        // В Paper/Spigot API 1.21.x locateNearestStructure(Structure,...) возвращает StructureSearchResult.
        org.bukkit.util.StructureSearchResult result =
                here.getWorld().locateNearestStructure(here, q.structure, q.structureRadius, false);
        if (result != null && result.getLocation().distanceSquared(here) < 1024) { // 32 блока
            addProgress(p.getUniqueId(), q, 1);
        }
    }

    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        progress.remove(id);
        lastProgressPing.remove(id);
        data.save(id);
    }

    /** Проверка инвентаря на наличие материала — для DEPOSIT_ITEM квестов.
     * Вызывается периодически или при пикапах/открытии меню. */
    public void checkDeposit(Player p) {
        QuestDefinition q = currentQuest(p.getUniqueId());
        if (q == null || q.type != QuestType.DEPOSIT_ITEM) return;
        if (q.itemMaterial == null) return;
        int has = 0;
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && item.getType() == q.itemMaterial) has += item.getAmount();
        }
        if (!q.extras.isEmpty()) {
            // Для нескольких материалов — прогресс считается по минимуму
            // среди прогресса по каждому требованию.
            int min = q.amount;
            for (var extra : q.extras) {
                int exHas = 0;
                for (ItemStack item : p.getInventory().getContents()) {
                    if (item != null && item.getType() == extra.material) exHas += item.getAmount();
                }
                int prog = Math.min(extra.amount, exHas);
                min = Math.min(min, prog);
            }
            // прогресс по основному материалу
            int mainProg = Math.min(q.amount, has);
            min = Math.min(min, mainProg);
            int already = currentProgress(p.getUniqueId(), q);
            if (min > already) addProgress(p.getUniqueId(), q, min - already);
            return;
        }
        if (has > 0) addProgress(p.getUniqueId(), q, has);
    }
}
