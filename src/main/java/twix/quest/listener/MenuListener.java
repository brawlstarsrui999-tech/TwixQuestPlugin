package twix.quest.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import twix.quest.TwixQuestPlugin;

public final class MenuListener implements Listener {
    private final TwixQuestPlugin plugin;
    public MenuListener(TwixQuestPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        plugin.getMenuManager().handleClick(e);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        plugin.getMenuManager().handleClose(e);
    }
}
