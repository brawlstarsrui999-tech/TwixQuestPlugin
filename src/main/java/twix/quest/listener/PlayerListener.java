package twix.quest.listener;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.entity.Strider;
import org.bukkit.inventory.ItemStack;
import twix.quest.TwixQuestPlugin;
import twix.quest.data.PlayerQuestData;
import twix.quest.hook.BuyerHook;
import twix.quest.manager.QuestManager;
import twix.quest.util.TextUtil;

import java.util.UUID;

public final class PlayerListener implements Listener, BuyerHook.TwixQuestSeller {

    private final TwixQuestPlugin plugin;
    private final PlayerQuestData data;
    private final QuestManager manager;
    private final BuyerHook hook;

    public PlayerListener(TwixQuestPlugin plugin, PlayerQuestData data, QuestManager manager) {
        this.plugin = plugin;
        this.data = data;
        this.manager = manager;
        this.hook = plugin.getBuyerHook();
        // подписываем мост в ServicesManager
        hook.registerBridge((p, mat, amount) -> {
            // Это хук от байер-плагина, если он его использует.
            ItemStack fake = new ItemStack(mat, amount);
            handleSell(p, fake, amount);
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        data.loadFile(id);
        data.startTime(id);
        data.setLastKnownName(e.getPlayer());

        // Поставить таймер проверок баланса и инвентаря каждую минуту
        new BukkitRunnable() {
            @Override public void run() {
                Player p = Bukkit.getPlayer(id);
                if (p == null || !p.isOnline()) { cancel(); return; }
                manager.checkBalance(p);
                manager.checkDeposit(p);
            }
        }.runTaskTimer(plugin, 20L * 30, 20L * 30);

        // Если уже есть текущий квест — проинформируем
        plugin.getQuestManager().currentQuest(id);
        TextUtil.send(e.getPlayer(), "<color:#9B59FF><b>Откройте <click:run_command:/quests>/quests</click></b> для просмотра своих заданий.");
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) { manager.onQuit(e); }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (e.isCancelled()) return;
        Material type = e.getBlock().getType();
        manager.onMineBlock(e, type);
    }

    @EventHandler
    public void onCraft(CraftItemEvent e) {
        if (e.isCancelled()) return;
        manager.onCraft(e);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if (e.getEntity().getKiller() == null) return;
        manager.onEntityKilled(e);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        manager.onWorldChange(e);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;
        manager.onMove(e);
    }

    @EventHandler
    public void onStriderMount(VehicleEnterEvent e) {
        if (!(e.getEntered() instanceof Player p)) return;
        if (e.getVehicle() instanceof Strider) {
            manager.onStriderMount(p);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.isCancelled()) return;
        manager.onTradeClick(e);
        if (e.getWhoClicked() instanceof Player p) {
            Bukkit.getScheduler().runTask(plugin, () -> manager.checkDeposit(p));
        }
    }

    @EventHandler
    public void onPickup(PlayerPickupItemEvent e) {
        if (e.isCancelled()) return;
        manager.checkDeposit(e.getPlayer());
    }

    /** Хук-перехватчик команд покупателя. Позволяет интегрироваться с
     * BaerPlugin без обязательного обратного хука. */
    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage();
        // Пример паттерна: /buyer sell, /buyer sell <amount>, /baer sell, /baer sell all
        String trimmed = msg.trim().toLowerCase();
        if (trimmed.startsWith("/buyer sell") || trimmed.startsWith("/baer sell") || trimmed.startsWith("/bay sell")) {
            // Отдаём менеджеру сигнал — мы не знаем точный предмет, но можем
            // попробовать зарегистрировать продажу по основной руке игрока.
            Player p = e.getPlayer();
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand != null && hand.getType() != Material.AIR) {
                manager.onSellToBuyer(p, hand);
            }
        }
    }

    /** Мост из байер-плагина — вызывается из его Bukkit ServicesManager (если он это поддерживает). */
    @Override
    public void handleSell(Player p, ItemStack stack, int amount) {
        manager.onSellToBuyer(p, stack);
    }
}
