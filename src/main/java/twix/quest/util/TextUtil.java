package twix.quest.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextUtil {

    private static MiniMessage MM;
    private static final Pattern HEX_PATTERN = Pattern.compile("&?#([0-9A-Fa-f]{6})");

    private TextUtil() {}

    public static void init(JavaPlugin plugin) {
        MM = MiniMessage.miniMessage();
    }

    /** Главный фиолетовый цвет плагина. */
    public static TextColor primary() { return TextColor.color(0x9B59FF); }
    /** Светлый акцент. */
    public static TextColor accent()  { return TextColor.color(0xD3A8FF); }
    /** Золотистый для наград. */
    public static TextColor gold()    { return TextColor.color(0xF1C40F); }
    /** Мягкий фон для подсказок. */
    public static TextColor soft()    { return TextColor.color(0xC5A3FF); }

    /** Переводит в §-кодированную строку (для legacy-цвета) путь. Не наш путь — мы на Adventure. */

    /** Сериализует текст, поддерживая HEX (#RRGGBB) и мнемоники &, наряду с MiniMessage тегами. */
    public static Component mm(String input) {
        if (input == null) return Component.empty();
        String prepared = processHexAndAmp(input);
        TagResolver resolver = TagResolver.standard();
        return MM.deserialize(prepared, resolver).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private static String processHexAndAmp(String input) {
        // Переводим &x -> <color>#x и &c -> <color>red и т.п.
        Matcher m = HEX_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, "<#" + m.group(1) + ">");
        }
        m.appendTail(sb);
        String result = sb.toString();

        // Legacy-коды для красоты в меню. Обрабатываем через MiniMessage тег color.
        result = processAmp(result, '&', '0', "black");
        result = processAmp(result, '&', '1', "dark_blue");
        result = processAmp(result, '&', '2', "dark_green");
        result = processAmp(result, '&', '3', "dark_aqua");
        result = processAmp(result, '&', '4', "dark_red");
        result = processAmp(result, '&', '5', "dark_purple");
        result = processAmp(result, '&', '6', "gold");
        result = processAmp(result, '&', '7', "gray");
        result = processAmp(result, '&', '8', "dark_gray");
        result = processAmp(result, '&', '9', "blue");
        result = processAmp(result, '&', 'a', "green");
        result = processAmp(result, '&', 'b', "aqua");
        result = processAmp(result, '&', 'c', "red");
        result = processAmp(result, '&', 'd', "light_purple");
        result = processAmp(result, '&', 'e', "yellow");
        result = processAmp(result, '&', 'f', "white");
        result = processAmp(result, '&', 'l', "<bold>");
        result = processAmp(result, '&', 'o', "<italic>");
        result = processAmp(result, '&', 'n', "<underlined>");
        result = processAmp(result, '&', 'm', "<strikethrough>");
        result = result.replace("&r", "<reset>");
        return result;
    }

    private static String processAmp(String input, char prefix, char code, String replacement) {
        String pattern = Pattern.quote(String.valueOf(prefix)) + code;
        return input.replaceAll("(?i)" + pattern, Matcher.quoteReplacement("<" + replacement + ">"));
    }

    /** Шлёт уведомление в чат фиолетовым префиксом. */
    public static void send(CommandSender to, String raw) {
        Component prefix = mm("<gradient:#BB8CFF:#9B59FF><bold>Twix</bold></gradient><gradient:#9B59FF:#D3A8FF>Quest</gradient> <dark_gray>»<reset> ").decoration(TextDecoration.ITALIC, false);
        Component body = mm(raw);
        to.sendMessage(prefix.append(body));
    }

    /** Шлёт бродкаст. */
    public static void broadcast(String raw) {
        Bukkit.broadcast(mm("<gradient:#9B59FF:#D3A8FF><b>TwixQuest</b></gradient> <dark_gray>»<reset> " + raw));
    }
}
