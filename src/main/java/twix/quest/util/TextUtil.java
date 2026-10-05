package twix.quest.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Утилиты форматирования. Использует только MiniMessage-совместимые теги.
 *
 * Поддерживаются:
 * - HEX-цвета:        &lt;#RRGGBB&gt;
 * - Именованные:      &lt;red&gt;, &lt;dark_purple&gt;, &lt;gold&gt;, и т.п.
 * - Градиенты:        &lt;gradient:#9B59FF:#D3A8FF&gt;Twix&lt;/gradient&gt;
 * - Жирный/наклон:   &lt;bold&gt;, &lt;italic&gt;, &lt;underlined&gt;
 * - Клики:            &lt;click:run_command:/quests&gt;открыть&lt;/click&gt;
 * - Hover:            &lt;hover:show_text:'описание'&gt;наведи&lt;/hover&gt;
 *
 * <p><b>Важно про цвета.</b> Цвета в MiniMessage — «незакрываемые» теги:
 * корректно писать &lt;#RRGGBB&gt; или &lt;gold&gt; БЕЗ парного &lt;/gold&gt;.
 * Незакрытый/несуществующий тег MiniMessage не отбрасывает, а выводит
 * буквально как текст (см. TokenParser: «not recognized, plain text» и
 * «the closing tag didn't match to anything» → TextNode). Поэтому строки
 * вида {@code "<##F1C40F>"} (двойной символ решётки) или {@code "</gold>"}
 * игрок видит прямо в названии/подсказке предмета.</p>
 *
 * <p>{@link #mm(String)} на всякий случай чинит обе эти опечатки и никогда
 * не бросает исключение — иначе одна кривая строка в quests.yml роняла бы
 * открытие всего меню.</p>
 */
public final class TextUtil {

    private static MiniMessage MM;
    private static JavaPlugin OWNER;
    /** Резолвер тегов: только стандартные MiniMessage-теги (HEX, named, gradient, decoration, click, hover). */
    private static final TagResolver RESOLVER = TagResolver.standard();

    /** Уже залогированные «починенные» строки, чтобы не спамить в консоль. */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    /** "<##F1C40F>" → "<#F1C40F>" (двойная решётка — всегда опечатка). */
    private static final Pattern DOUBLE_HEX = Pattern.compile("<#(?=#)");
    /** "</#F1C40F>" — закрывающий HEX-тег, которого в MiniMessage не существует. */
    private static final Pattern CLOSE_HEX = Pattern.compile("</\\s*#[0-9a-fA-F]{6}\\s*>");
    /** "</gold>" и прочие закрывающие теги именованных цветов. */
    private static final Pattern CLOSE_NAMED = Pattern.compile("</\\s*([a-zA-Z_]+)\\s*>");

    /** Именованные цвета NamedTextColor: их закрывать нельзя. */
    private static final Set<String> NAMED_COLORS = Set.of(
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
            "gold", "gray", "grey", "dark_gray", "dark_grey", "blue", "green", "aqua",
            "red", "light_purple", "yellow", "white");

    private TextUtil() {}

    public static void init(JavaPlugin plugin) {
        MM = MiniMessage.miniMessage();
        OWNER = plugin;
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
        String safe = sanitize(input);
        try {
            return MM.deserialize(safe, RESOLVER)
                    .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        } catch (RuntimeException ex) {
            // МиниМесседж умеет кидаться на совсем битых конструкциях — не роняем меню,
            // а показываем текст без разметки.
            warnOnce(input, ex.getMessage());
            return MM.deserialize(MM.escapeTags(stripTags(safe)))
                    .decoration(TextDecoration.ITALIC, false);
        }
    }

    /**
     * Чинит типовые опечатки в цветовых тегах, из-за которых MiniMessage
     * выводит разметку как обычный текст.
     */
    public static String sanitize(String input) {
        if (input == null || input.isEmpty()) return input;
        String out = input;

        // 1) "<##RRGGBB>" → "<#RRGGBB>"
        if (out.contains("<##")) {
            out = DOUBLE_HEX.matcher(out).replaceAll("<#");
        }

        // 2) "</#RRGGBB>" — закрыть HEX-цвет нельзя, тег уйдёт в чат/лор текстом
        if (out.contains("</#")) {
            out = CLOSE_HEX.matcher(out).replaceAll("");
        }

        // 3) "</gold>", "</red>", ... — то же самое для именованных цветов
        if (out.contains("</")) {
            Matcher m = CLOSE_NAMED.matcher(out);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String name = m.group(1).toLowerCase(Locale.ROOT);
                m.appendReplacement(sb, NAMED_COLORS.contains(name) ? "" : Matcher.quoteReplacement(m.group()));
            }
            m.appendTail(sb);
            out = sb.toString();
        }

        if (!out.equals(input)) {
            warnOnce(input, "исправлен некорректный цветовой тег (цвета не закрываются: пишите <#RRGGBB> или <gold> без </...>)");
        }
        return out;
    }

    /** Грубо вырезает все теги &lt;...&gt; — используется только как аварийный фолбэк. */
    private static String stripTags(String input) {
        return input.replaceAll("<[^<>]{0,64}>", "");
    }

    private static void warnOnce(String input, String why) {
        if (WARNED.size() > 200 || !WARNED.add(input)) return;
        String message = "TwixQuest: некорректная MiniMessage-строка \"" + trim(input) + "\" — " + why;
        if (OWNER != null) OWNER.getLogger().warning(message);
        else Bukkit.getLogger().warning(message);
    }

    private static String trim(String s) {
        return s.length() > 80 ? s.substring(0, 80) + "…" : s;
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

    // ------------------------------------------------------------------
    // Названия и форматирование для меню
    // ------------------------------------------------------------------

    /**
     * Название предмета на языке КЛИЕНТА: русский клиент увидит «Дубовое бревно»,
     * английский — «Oak Log». Не нужен словарь переводов.
     */
    public static String itemName(Material material) {
        if (material == null) return "?";
        try {
            return "<lang:" + material.translationKey() + ">";
        } catch (RuntimeException ex) {
            // у материала нет ключа перевода — покажем читаемое имя вместо падения меню
            return humanize(material.name());
        }
    }

    /** DIAMOND_PICKAXE → «Diamond pickaxe» (запасной вариант, когда нет перевода). */
    static String humanize(String enumName) {
        String s = enumName.toLowerCase(Locale.ROOT).replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Название существа на языке клиента. */
    public static String entityName(EntityType type) {
        if (type == null) return "?";
        try {
            return "<lang:" + type.translationKey() + ">";
        } catch (RuntimeException ex) {
            return type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        }
    }

    /** «Острота II» на языке клиента; уровень не пишем для чаров с единственным уровнем. */
    public static String enchantName(String key, int level, int maxLevel) {
        String name = "<lang:enchantment.minecraft." + key + ">";
        if (level == 1 && maxLevel == 1) return name;
        return name + " " + roman(level);
    }

    /** Римские цифры для уровней чаров (1–10), дальше — обычные числа. */
    public static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 1 && n <= 10 ? r[n] : String.valueOf(n);
    }

    /** Склонение по числу: 1 монета, 2 монеты, 5 монет. */
    public static String plural(long n, String one, String few, String many) {
        long a = Math.abs(n) % 100;
        long b = a % 10;
        if (a >= 11 && a <= 14) return many;
        if (b == 1) return one;
        if (b >= 2 && b <= 4) return few;
        return many;
    }

    /** Число с пробелами между тысячами: 1500 → «1 500». */
    public static String num(long n) {
        return String.format(Locale.US, "%,d", n).replace(',', ' ');
    }

    /** Прогресс-бар строкой MiniMessage: ▰▰▰▱▱▱▱▱▱▱ (цвет не закрываем — MiniMessage этого не требует). */
    public static String bar(long done, long total, int segments) {
        if (total <= 0) return "<dark_gray>" + "▱".repeat(Math.max(1, segments));
        long clamped = Math.max(0, Math.min(done, total));
        int filled = (int) Math.round(clamped * (double) segments / total);
        if (clamped > 0 && filled == 0) filled = 1;          // хоть что-то видно, если прогресс есть
        if (clamped < total && filled == segments) filled = segments - 1; // «полно» только когда реально полно
        StringBuilder sb = new StringBuilder("<#9B59FF>");
        for (int i = 0; i < segments; i++) {
            if (i == filled) sb.append("<dark_gray>");
            sb.append(i < filled ? "▰" : "▱");
        }
        return sb.toString();
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
