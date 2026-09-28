package twix.quest.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Утилиты форматирования. Использует только MiniMessage-совместимые теги.
 *
 * Поддерживаются:
 * - HEX-цвета:        &lt;#RRGGBB&gt;Hello&lt;/#RRGGBB&gt;
 * - Именованные:      &lt;red&gt;, &lt;dark_purple&gt;, &lt;gold&gt;, и т.п.
 * - Градиенты:        &lt;gradient:#9B59FF:#D3A8FF&gt;Twix&lt;/gradient&gt;
 * - Жирный/наклон:   &lt;bold&gt;, &lt;italic&gt;, &lt;underlined&gt;
 * - Клики:            &lt;click:run_command:/quests&gt;открыть&lt;/click&gt;
 * - Hover:            &lt;hover:show_text:'описание'&gt;наведи&lt;/hover&gt;
 *
 * Не используются: &-коды и &lt;color:#hex&gt; — для совместимости с любыми версиями Adventure.
 */
public final class TextUtil {

    private static MiniMessage MM;
    /** Резолвер тегов: только стандартные MiniMessage-теги (HEX, named, gradient, decoration, click, hover). */
    private static final TagResolver RESOLVER = TagResolver.standard();

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
    /** Тёмный фон. */
    public static TextColor deep()    { return TextColor.color(0x4B0082); }
    /** Зелёный «выполнено». */
    public static TextColor done()    { return TextColor.color(0x27AE60); }

    /** Парсит строку с MiniMessage-тегами в {@link Component}, убирает дефолтный курсив. */
    public static Component mm(String input) {
        if (input == null || input.isEmpty()) return Component.empty();
        return MM.deserialize(input, RESOLVER).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /**
     * Экранирует символы MiniMessage в произвольной строке (например, имя игрока),
     * чтобы они не были интерпретированы как теги. Использует встроенный
     * {@link MiniMessage#escapeTags(String)} — добавлен в Adventure 4.10.0+.
     */
    public static String escapeMiniMessage(String input) {
        if (input == null) return "";
        return MM.escapeTags(input);
    }

    /**
     * Шлёт уведомление в чат с фиолетовым префиксом TwixQuest.
     */
    public static void send(CommandSender to, String raw) {
        Component prefix = mm("<gradient:#BB8CFF:#9B59FF><bold>Twix</bold></gradient>" +
                "<gradient:#9B59FF:#D3A8FF>Quest</gradient> <dark_gray>»<reset> ")
                .decoration(TextDecoration.ITALIC, false);
        Component body = mm(raw);
        to.sendMessage(prefix.append(body));
    }

    /** Бродкаст для всех игроков. */
    public static void broadcast(String raw) {
        Bukkit.broadcast(mm("<gradient:#9B59FF:#D3A8FF><bold>TwixQuest</bold></gradient> <dark_gray>»<reset> " + raw));
    }

    /** Просто отправить уже готовый Component (без префикса). */
    public static void sendComponent(CommandSender to, Component c) {
        to.sendMessage(c);
    }
}
