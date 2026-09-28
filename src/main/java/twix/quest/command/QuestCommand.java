package twix.quest.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import twix.quest.TwixQuestPlugin;
import twix.quest.menu.MenuManager;

import java.util.List;

public final class QuestCommand implements CommandExecutor, TabCompleter {

    private final TwixQuestPlugin plugin;
    private final MenuManager menus;

    public QuestCommand(TwixQuestPlugin plugin, MenuManager menus) {
        this.plugin = plugin;
        this.menus = menus;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Эту команду может выполнить только игрок.");
            return true;
        }
        menus.openMain(p);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {
        return List.of();
    }
}
