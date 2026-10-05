package twix.quest.hook;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import twix.quest.TwixQuestPlugin;
import twix.quest.manager.QuestManager;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Интеграция с плагином байера (brawlstarsrui999-tech/BaerPlugin, имя плагина
 * в plugin.yml — {@code BuyerPlugin}) БЕЗ каких-либо правок самого байера.
 *
 * <p><b>Как реально устроен байер</b> (проверено по исходникам
 * {@code com.buyerplugin.ShopGUI}):</p>
 * <ul>
 *     <li>команда {@code /buyer} (алиасы {@code shop}, {@code магазин}) открывает
 *         GUI на 54 слота с заголовком {@code §5§lБайер §8» §7<категория>};</li>
 *     <li>никакого {@code /buyer sell} не существует — продажа это ПРАВЫЙ клик
 *         по товару, покупка — ЛЕВЫЙ (shift — по 64 штуки);</li>
 *     <li>после каждой сделки байер вызывает {@code refreshLater(...)}, то есть
 *         на следующем тике открывает НОВЫЙ инвентарь. Bukkit при этом сначала
 *         шлёт {@link InventoryCloseEvent} по старому, потом
 *         {@link InventoryOpenEvent} по новому.</li>
 * </ul>
 *
 * <p><b>Что делаем мы:</b></p>
 * <ol>
 *     <li>Отмечаем «сессию байера» — по команде {@code /buyer} ИЛИ по заголовку
 *         открывшегося GUI (ключевые слова настраиваются в quests.yml).</li>
 *     <li>Запоминаем снимок инвентаря и баланс игрока.</li>
 *     <li>На каждом закрытии (в том числе на автообновлении GUI байером) считаем
 *         разницу: чего стало меньше — продано, чего больше — куплено, и отдаём
 *         это в {@link QuestManager#onBuyerTrade}.</li>
 *     <li>Баланс Vault используется только как ПОДСКАЗКА (продажа или покупка),
 *         поэтому квесты работают даже без подключённой экономики.</li>
 * </ol>
 *
 * <p>Если байер не установлен — сессия просто никогда не начнётся, и квесты
 * SELL_TO_BUYER / BUY_FROM_BUYER не будут засчитываться.</p>
 */
public final class BuyerHook implements Listener {

    /** Сколько живёт сессия без активности (мс). */
    private static final long SESSION_TTL_MS = 10L * 60L * 1000L;
    /**
     * Сессия, начатая командой, должна подтвердиться открытием GUI байера за это время (мс).
     * Иначе (команда чужого плагина, нет прав) любое изменение инвентаря в ближайшие
     * минуты могло бы ошибочно засчитаться продажей.
     */
    private static final long GUI_CONFIRM_MS = 5_000L;
    /** Период дополнительной сверки активных сессий (тики). */
    private static final long SETTLE_PERIOD_TICKS = 20L;

    private static final List<String> DEFAULT_TITLE_KEYWORDS =
            List.of("байер", "баер", "buyer", "baer");
    private static final List<String> DEFAULT_COMMAND_LABELS =
            List.of("buyer", "baer", "shop", "магазин");

    private final JavaPlugin plugin;
    private final QuestManager manager;
    private final Map<UUID, Session> sessions = new HashMap<>();

    private BuyerHook(JavaPlugin plugin, QuestManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public static BuyerHook create(JavaPlugin plugin, QuestManager manager) {
        BuyerHook hook = new BuyerHook(plugin, manager);
        Bukkit.getPluginManager().registerEvents(hook, plugin);
        // Дополнительная сверка: если будущая версия байера перестанет
        // переоткрывать GUI, сделки всё равно засчитаются.
        Bukkit.getScheduler().runTaskTimer(plugin, hook::settleAll,
                SETTLE_PERIOD_TICKS, SETTLE_PERIOD_TICKS);
        return hook;
    }

    public void unhook() {
        sessions.clear();
    }

    // ------------------------------------------------------------------
    // События
    // ------------------------------------------------------------------

    /** /buyer (и алиасы) — старт сессии, даже если заголовок GUI когда-нибудь поменяют. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage();
        if (msg == null || msg.length() < 2 || msg.charAt(0) != '/') return;
        String rest = msg.substring(1);
        int sp = rest.indexOf(' ');
        String label = (sp < 0 ? rest : rest.substring(0, sp)).toLowerCase(Locale.ROOT);
        if (label.isEmpty()) return;
        if (commandLabels().contains(label)) {
            startSession(e.getPlayer(), "команда /" + label);
        }
    }

    /** GUI с «байерским» заголовком — старт/продолжение сессии. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        boolean buyerGui = isBuyerTitle(e.getView().title());
        if (buyerGui) {
            startSession(p, "заголовок GUI");
            Session confirmed = sessions.get(p.getUniqueId());
            if (confirmed != null) confirmed.guiConfirmed = true;
        } else {
            // Игрок ушёл в обычный сундук/верстак — сессия байера закончилась,
            // иначе следующая сверка могла бы засчитать посторонние изменения.
            sessions.remove(p.getUniqueId());
        }
    }

    /**
     * Закрытие GUI: считаем сделку. Байер после каждой продажи/покупки
     * переоткрывает окно, поэтому именно здесь мы видим результат.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        settle(p);
        // Сессию закрываем в любом случае: если байер переоткрыл окно,
        // onOpen тут же начнёт новую со свежим снимком. Так не остаётся
        // «висящих» сессий, которые могли бы засчитать посторонние изменения.
        sessions.remove(p.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        sessions.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------
    // Сессия
    // ------------------------------------------------------------------

    private void startSession(Player p, String reason) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null) {
            s = new Session();
            sessions.put(p.getUniqueId(), s);
            s.reason = reason;
            s.startedAt = System.currentTimeMillis();
        }
        s.items = snapshotItems(p.getInventory());
        s.balance = currentBalance(p);
        s.lastActivity = System.currentTimeMillis();
    }

    private void settleAll() {
        if (sessions.isEmpty()) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                settle(p);
            } catch (Throwable t) {
                plugin.getLogger().warning("Сверка сделки с байером не удалась: " + t);
            }
        }
    }

    /**
     * Сравнивает текущий инвентарь со снимком и засчитывает разницу.
     * Снимок после сверки обновляется — повторного засчёта не будет.
     */
    private void settle(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null) return;
        long now = System.currentTimeMillis();
        if (now - s.lastActivity > SESSION_TTL_MS
                || (!s.guiConfirmed && now - s.startedAt > GUI_CONFIRM_MS)) {
            sessions.remove(p.getUniqueId());
            return;
        }

        Map<Material, Integer> after = snapshotItems(p.getInventory());
        Map<Material, Integer> sold = decrease(s.items, after);
        Map<Material, Integer> bought = decrease(after, s.items);
        s.items = after;
        s.lastActivity = now;
        if (sold.isEmpty() && bought.isEmpty()) return;

        // Баланс — только подсказка, работает и без Vault.
        double balance = currentBalance(p);
        double delta = balance - s.balance;
        s.balance = balance;
        if (hasEconomy() && Math.abs(delta) > 0.005) {
            if (delta < 0) sold.clear();   // деньги ушли — это покупка
            else bought.clear();           // деньги пришли — это продажа
        }
        if (sold.isEmpty() && bought.isEmpty()) return;

        try {
            manager.onBuyerTrade(p, sold, bought);
        } catch (Throwable t) {
            plugin.getLogger().warning("Не удалось засчитать сделку с байером: " + t);
        }
    }

    // ------------------------------------------------------------------
    // Утилиты
    // ------------------------------------------------------------------

    /** Заголовок похож на GUI байера? Текст сравниваем без цветовых кодов. */
    private boolean isBuyerTitle(Component title) {
        if (title == null) return false;
        String plain = PlainTextComponentSerializer.plainText().serialize(title);
        if (plain == null || plain.isEmpty()) return false;
        String t = plain.toLowerCase(Locale.ROOT).replace("§", "");
        for (String key : titleKeywords()) {
            if (t.contains(key)) return true;
        }
        return false;
    }

    /** Кэш настроек: команды приходят часто, перечитывать YAML каждый раз незачем. */
    private static final long SETTINGS_CACHE_MS = 2000L;
    private long settingsCachedAt;
    private List<String> cachedTitleKeywords = DEFAULT_TITLE_KEYWORDS;
    private List<String> cachedCommandLabels = DEFAULT_COMMAND_LABELS;

    private void refreshSettingsCache() {
        long now = System.currentTimeMillis();
        if (now - settingsCachedAt < SETTINGS_CACHE_MS) return;
        settingsCachedAt = now;
        TwixQuestPlugin tq = TwixQuestPlugin.inst();
        if (tq == null || tq.getQuestRegistry() == null) {
            cachedTitleKeywords = DEFAULT_TITLE_KEYWORDS;
            cachedCommandLabels = DEFAULT_COMMAND_LABELS;
            return;
        }
        cachedTitleKeywords = tq.getQuestRegistry().settingsList("buyer-gui-keywords", DEFAULT_TITLE_KEYWORDS);
        cachedCommandLabels = tq.getQuestRegistry().settingsList("buyer-command-labels", DEFAULT_COMMAND_LABELS);
    }

    private List<String> titleKeywords() {
        refreshSettingsCache();
        return cachedTitleKeywords;
    }

    private List<String> commandLabels() {
        refreshSettingsCache();
        return cachedCommandLabels;
    }

    private boolean hasEconomy() {
        TwixQuestPlugin tq = TwixQuestPlugin.inst();
        return tq != null && tq.getVaultEconomy() != null;
    }

    private double currentBalance(Player p) {
        try {
            TwixQuestPlugin tq = TwixQuestPlugin.inst();
            Economy eco = tq == null ? null : tq.getVaultEconomy();
            return eco == null ? 0.0 : eco.getBalance(p);
        } catch (Throwable t) {
            return 0.0;
        }
    }

    /** Плоский снимок инвентаря: материал → суммарное количество. */
    private Map<Material, Integer> snapshotItems(PlayerInventory inv) {
        Map<Material, Integer> map = new HashMap<>();
        if (inv == null) return map;
        for (ItemStack stack : inv.getContents()) {
            if (stack == null || stack.getType() == Material.AIR) continue;
            map.merge(stack.getType(), stack.getAmount(), Integer::sum);
        }
        return map;
    }

    /** Чего в {@code after} стало меньше, чем в {@code before} (и на сколько). */
    private Map<Material, Integer> decrease(Map<Material, Integer> before, Map<Material, Integer> after) {
        Map<Material, Integer> out = new HashMap<>();
        for (Map.Entry<Material, Integer> e : before.entrySet()) {
            int diff = e.getValue() - after.getOrDefault(e.getKey(), 0);
            if (diff > 0) out.put(e.getKey(), diff);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Совместимость: прямой мост для тех, кто захочет звать нас из своего байера
    // ------------------------------------------------------------------

    /** Прямое уведомление о продаже (например, из кастомного байер-плагина). */
    public void notifySale(Player p, ItemStack stack, double amount) {
        if (stack == null) return;
        manager.onSellToBuyer(p, stack);
    }

    public void registerBridge(TwixQuestManagerBridge bridge) {
        Bukkit.getServicesManager().register(TwixQuestManagerBridge.class, bridge, plugin,
                org.bukkit.plugin.ServicePriority.Normal);
    }

    public interface SellListener {
        void onSell(Player p, ItemStack stack, double amount);
    }

    public interface TwixQuestManagerBridge {
        void notifySell(Player p, Material material, int amount);
    }

    public interface TwixQuestSeller {
        void handleSell(Player p, ItemStack stack, int amount);
    }

    /** Диапазон слотов байера не используется — оставляем для отладки. */
    public boolean hasSession(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    // ------------------------------------------------------------------
    // Внутренние классы
    // ------------------------------------------------------------------

    private static final class Session {
        String reason = "";
        double balance;
        long startedAt;
        long lastActivity;
        /** Открылось ли настоящее GUI байера (а не только прозвучала команда). */
        boolean guiConfirmed;
        Map<Material, Integer> items = new HashMap<>();
    }
}
