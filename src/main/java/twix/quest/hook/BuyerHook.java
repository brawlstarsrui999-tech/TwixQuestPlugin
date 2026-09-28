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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import twix.quest.TwixQuestPlugin;
import twix.quest.manager.QuestManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Хук для интеграции с BaerPlugin (brawlstarsrui999-tech/BaerPlugin) без
 * необходимости модификации самого BaerPlugin.
 *
 * <p><b>Принцип работы (inventory-snapshot):</b></p>
 * <ol>
 *     <li>Когда игрок открывает GUI с заголовком, содержащим "Байер" (или "Buyer"),
 *         мы запоминаем (snapshot) текущее содержимое его инвентаря и баланс Vault.</li>
 *     <li>Когда игрок закрывает это GUI, мы делаем diff между snapshot'ом и текущим
 *         состоянием:
 *         <ul>
 *             <li>Если баланс ВЫРОС — игрок продал что-то байеру. Находим материалы,
 *                 которых стало меньше в инвентаре, и засчитываем их как
 *                 {@code SELL_TO_BUYER}.</li>
 *             <li>Если баланс УПАЛ — игрок купил у байера (плюс инвентарь пополнился).
 *                 Это можно использовать для квестов типа "купить у байера" (если такие будут).</li>
 *         </ul>
 *     </li>
 * </ol>
 *
 * <p>Подход полностью не зависит от BaerPlugin — мы лишь смотрим на то, что
 * инвентарь/баланс игрока изменились, пока он находился в GUI с заголовком "Байер".</p>
 *
 * <p>Если BaerPlugin не установлен — квесты SELL_TO_BUYER просто не будут
 * засчитываться (потому что GUI с "Байер" не откроется).</p>
 */
public final class BuyerHook implements Listener {

    private final JavaPlugin plugin;
    private final QuestManager manager;
    private final Map<UUID, Snapshot> snapshots = new WeakHashMap<>();

    private BuyerHook(JavaPlugin plugin, QuestManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    public static BuyerHook create(JavaPlugin plugin, QuestManager manager) {
        BuyerHook hook = new BuyerHook(plugin, manager);
        Bukkit.getPluginManager().registerEvents(hook, plugin);
        return hook;
    }

    /** Колбэк из main — больше ничего делать не нужно, listener уже зарегистрирован. */
    public void hook() { /* no-op: события слушаются через @EventHandler */ }
    public void unhook() { /* no-op: Bukkit сам отпишется при onDisable() */ }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (!isBuyerGui(e.getView().title())) return;
        if (!p.isOnline()) return;

        Snapshot snap = new Snapshot();
        snap.balance = currentBalance(p);
        snap.items = snapshotItems(p.getInventory());
        snapshots.put(p.getUniqueId(), snap);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Snapshot snap = snapshots.remove(p.getUniqueId());
        if (snap == null) return;
        if (!isBuyerGui(e.getView().title())) return;
        if (!p.isOnline()) return;

        // Diff баланса
        double newBalance = currentBalance(p);
        double delta = newBalance - snap.balance;

        Map<Material, Integer> oldItems = snap.items;
        Map<Material, Integer> newItems = snapshotItems(p.getInventory());

        // Продажа: баланс вырос И какие-то предметы убавились.
        if (delta > 0.0001) {
            // Найдём материалы, которых стало меньше (т.е. игрок продал их).
            for (Map.Entry<Material, Integer> entry : oldItems.entrySet()) {
                Material mat = entry.getKey();
                int was = entry.getValue();
                int now = newItems.getOrDefault(mat, 0);
                int sold = was - now;
                if (sold > 0) {
                    ItemStack fake = new ItemStack(mat, sold);
                    try {
                        manager.onSellToBuyer(p, fake);
                    } catch (Throwable t) {
                        plugin.getLogger().warning("onSellToBuyer не сработал: " + t.getMessage());
                    }
                }
            }
        }
        // Покупка: баланс упал И какие-то предметы прибавились.
        else if (delta < -0.0001) {
            // Можно засчитывать как "купил у байера", но у нас таких квестов нет —
            // оставляем хук на будущее. Не логируем, чтобы не спамить.
        }
    }

    // ------------------------------------------------------------------
    // Утилиты
    // ------------------------------------------------------------------

    /**
     * Возвращает true если заголовок inventory похож на GUI байера.
     * Содержит "Байер" / "Buyer" / "магазин" — на случай, если BaerPlugin
     * сменит title. Проверка идёт по plain text без цветовых кодов.
     */
    private boolean isBuyerGui(Component title) {
        if (title == null) return false;
        String plain = PlainTextComponentSerializer.plainText().serialize(title);
        if (plain == null) return false;
        String t = plain.toLowerCase();
        return t.contains("байер") || t.contains("buyer") || t.contains("магазин");
    }

    private double currentBalance(Player p) {
        try {
            TwixQuestPlugin tq = TwixQuestPlugin.inst();
            Economy eco = tq == null ? null : tq.getVaultEconomy();
            if (eco == null) return 0.0;
            return eco.getBalance(p);
        } catch (Throwable t) {
            return 0.0;
        }
    }

    /**
     * Делает плоский snapshot инвентаря игрока (сумма по каждому Material).
     * Не учитывает meta-данные (имя, зачарования) — только тип и количество.
     */
    private Map<Material, Integer> snapshotItems(PlayerInventory inv) {
        Map<Material, Integer> map = new HashMap<>();
        if (inv == null) return map;
        for (ItemStack stack : inv.getContents()) {
            if (stack == null || stack.getType() == Material.AIR) continue;
            map.merge(stack.getType(), stack.getAmount(), Integer::sum);
        }
        return map;
    }

    /** Совместимость со старым API (используется PlayerListener'ом). */
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

    // ------------------------------------------------------------------
    // Внутренний класс
    // ------------------------------------------------------------------
    private static final class Snapshot {
        double balance;
        Map<Material, Integer> items = new HashMap<>();
    }
}
