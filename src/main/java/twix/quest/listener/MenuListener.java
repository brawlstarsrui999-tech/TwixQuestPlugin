package twix.quest.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import twix.quest.TwixQuestPlugin;

public final class MenuListener implements Listener {
    private final TwixQuestPlugin plugin;
    public MenuListener(TwixQuestPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        plugin.getMenuManager().handleClick(e);
    }

    /** Перетаскивание предметов через наше меню запрещено — иначе они пропали бы при закрытии окна. */
    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        plugin.getMenuManager().handleDrag(e);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        plugin.getMenuManager().handleClose(e);
    }
}
