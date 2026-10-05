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
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Strider;
import org.bukkit.inventory.ItemStack;
import twix.quest.TwixQuestPlugin;
import twix.quest.data.PlayerQuestData;
import twix.quest.hook.BuyerHook;
import twix.quest.manager.QuestManager;
import twix.quest.quest.QuestDefinition;
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
        // Подписываем устаревший мост на случай, если у кого-то стоит кастомный
        // BuyerPlugin, использующий TwixQuestManagerBridge (обратная совместимость).
        hook.registerBridge((p, mat, amount) -> {
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

        TextUtil.send(e.getPlayer(), "<#9B59FF><b>Откройте <click:run_command:/quests>/quests</click></b> для просмотра своих заданий.");
        QuestDefinition current = manager.currentQuest(id);
        if (current != null) {
            TextUtil.send(e.getPlayer(), "<gray>Текущий квест: <#D3A8FF>#" + current.id + " " + current.name);
        }
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

    /**
     * Оседлать лавомерку. Слушаем EntityMountEvent (любой «посадочный» случай), а не
     * VehicleEnterEvent: так один раз посадка не засчитается дважды.
     */
    @EventHandler
    public void onMount(EntityMountEvent e) {
        if (e.isCancelled()) return;
        if (e.getEntity() instanceof Player p && e.getMount() instanceof Strider) {
            manager.onStriderMount(p);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.isCancelled()) return;
        manager.onTradeClick(e);
    }

    // ВАЖНО: команды байера здесь НЕ перехватываем.
    // В BaerPlugin (имя плагина — BuyerPlugin) нет и не было подкоманды "sell":
    // продажа это правый клик по товару в GUI /buyer, покупка — левый.
    // Старый перехватчик "/buyer sell" засчитывал предмет из основной руки,
    // хотя никакой сделки не происходило. Теперь командами занимается BuyerHook:
    // он лишь отмечает начало сессии, а сделку считает по изменению инвентаря.

    /** Мост из байер-плагина — вызывается из его Bukkit ServicesManager (если он это поддерживает). */
    @Override
    public void handleSell(Player p, ItemStack stack, int amount) {
        manager.onSellToBuyer(p, stack);
    }
}
