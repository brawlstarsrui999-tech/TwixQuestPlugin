package twix.quest.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import twix.quest.TwixQuestPlugin;
import twix.quest.data.PlayerQuestData;
import twix.quest.data.QuestRegistry;
import twix.quest.quest.QuestDefinition;
import twix.quest.util.TextUtil;
import twix.quest.manager.QuestManager;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class QuestAdminCommand implements CommandExecutor, TabCompleter {

    private final TwixQuestPlugin plugin;
    private final QuestManager manager;
    private final QuestRegistry registry;
    private final PlayerQuestData data;

    public QuestAdminCommand(TwixQuestPlugin plugin, QuestManager manager, QuestRegistry registry, PlayerQuestData data) {
        this.plugin = plugin;
        this.manager = manager;
        this.registry = registry;
        this.data = data;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {
        if (!sender.hasPermission("twixquest.admin")) {
            TextUtil.send(sender, "<red>У вас нет прав.");
            return true;
        }
        if (args.length == 0) { help(sender); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadAll();
                TextUtil.send(sender, "<#9B59FF>Конфигурация и квесты перезагружены.");
            }
            case "reset" -> {
                if (args.length < 2) { TextUtil.send(sender, "<red>Использование: /tqadmin reset <player>"); return true; }
                Player p = Bukkit.getPlayerExact(args[1]);
                if (p == null) { TextUtil.send(sender, "<red>Игрок не найден."); return true; }
                data.reset(p.getUniqueId());
                TextUtil.send(sender, "<#9B59FF>Квесты игрока " + p.getName() + " сброшены.");
            }
            case "complete" -> {
                if (args.length < 3) { TextUtil.send(sender, "<red>Использование: /tqadmin complete <player> <questId|active>"); return true; }
                Player p = Bukkit.getPlayerExact(args[1]);
                if (p == null) { TextUtil.send(sender, "<red>Игрок не найден."); return true; }
                UUID id = p.getUniqueId();
                if (args[2].equalsIgnoreCase("active")) {
                    manager.forceCompleteCurrent(id);
                } else {
                    try {
                        int qid = Integer.parseInt(args[2]);
                        QuestDefinition q = registry.byId(qid);
                        if (q == null) { TextUtil.send(sender, "<red>Неизвестный квест #" + qid); return true; }
                        manager.completeQuest(id, q);
                    } catch (NumberFormatException ex) { TextUtil.send(sender, "<red>Неверный номер квеста."); }
                }
            }
            case "info" -> {
                if (args.length < 2) { TextUtil.send(sender, "<red>Использование: /tqadmin info <player>"); return true; }
                Player p = Bukkit.getPlayerExact(args[1]);
                if (p == null) { TextUtil.send(sender, "<red>Игрок не найден."); return true; }
                int done = data.countMainCompleted(p.getUniqueId(), registry);
                TextUtil.send(sender, "<#9B59FF>Игрок " + p.getName() + ": пройдено <#F1C40F>" + done + "/" + registry.size() + "<reset> основных квестов.");
                long started = data.startTime(p.getUniqueId());
                TextUtil.send(sender, "<gray>  Старт прогресса: " + new java.util.Date(started));
            }
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage("§d/tqadmin reload §7— перезагрузка конфигурации");
        sender.sendMessage("§d/tqadmin reset <player> §7— сброс прогресса");
        sender.sendMessage("§d/tqadmin complete <player> <active|questId> §7— завершить квест");
        sender.sendMessage("§d/tqadmin info <player> §7— информация о прогрессе");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String[] args) {
        if (args.length == 1) return Arrays.asList("reload", "reset", "complete", "info");
        if (args.length == 2) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("complete")) {
            java.util.List<String> ids = new java.util.ArrayList<>();
            ids.add("active");
            for (QuestDefinition q : registry.all()) ids.add(String.valueOf(q.id));
            return ids;
        }
        return List.of();
    }
}
