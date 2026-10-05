package twix.quest.util;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextUtilTest {

    @BeforeAll
    static void init() {
        TextUtil.init(null);
    }

    private static String plain(String mini) {
        return PlainTextComponentSerializer.plainText().serialize(TextUtil.mm(mini));
    }

    private static long count(String s, char c) {
        return s.chars().filter(ch -> ch == c).count();
    }

    @Test
    void russianPlurals() {
        assertEquals("монета", TextUtil.plural(1, "монета", "монеты", "монет"));
        assertEquals("монеты", TextUtil.plural(2, "монета", "монеты", "монет"));
        assertEquals("монеты", TextUtil.plural(4, "монета", "монеты", "монет"));
        assertEquals("монет", TextUtil.plural(5, "монета", "монеты", "монет"));
        assertEquals("монет", TextUtil.plural(11, "монета", "монеты", "монет"));
        assertEquals("монет", TextUtil.plural(14, "монета", "монеты", "монет"));
        assertEquals("монета", TextUtil.plural(21, "монета", "монеты", "монет"));
        assertEquals("монеты", TextUtil.plural(22, "монета", "монеты", "монет"));
        assertEquals("монет", TextUtil.plural(1500, "монета", "монеты", "монет"));
        assertEquals("уровней", TextUtil.plural(30, "уровень", "уровня", "уровней"));
    }

    @Test
    void numbersAndRoman() {
        assertEquals("1 500", TextUtil.num(1500));
        assertEquals("750", TextUtil.num(750));
        assertEquals("II", TextUtil.roman(2));
        assertEquals("IV", TextUtil.roman(4));
        assertEquals("V", TextUtil.roman(5));
        assertEquals("12", TextUtil.roman(12));
    }

    @Test
    void enchantNamesUseClientLanguageKeys() {
        assertEquals("<lang:enchantment.minecraft.sharpness> II", TextUtil.enchantName("sharpness", 2, 5));
        assertEquals("<lang:enchantment.minecraft.mending>", TextUtil.enchantName("mending", 1, 1));
        assertEquals("<lang:enchantment.minecraft.fire_aspect> I", TextUtil.enchantName("fire_aspect", 1, 2));
    }

    @Test
    void progressBar() {
        String empty = TextUtil.bar(0, 10, 10);
        String half = TextUtil.bar(5, 10, 10);
        String full = TextUtil.bar(10, 10, 10);
        assertEquals(0, count(empty, '▰'));
        assertEquals(5, count(half, '▰'));
        assertEquals(10, count(full, '▰'));
        assertEquals(10, count(empty, '▱') + count(empty, '▰'));
        // 1 из 750 — полоска уже не пустая, 749 из 750 — ещё не полная
        assertTrue(count(TextUtil.bar(1, 750, 10), '▰') >= 1);
        assertTrue(count(TextUtil.bar(749, 750, 10), '▰') < 10);
        // рендерится без остатков разметки
        assertFalse(plain(half).contains("<"));
        assertEquals(10, plain(half).length());
    }

    @Test
    void sanitizeFixesBrokenColorTags() {
        assertEquals("<#F1C40F>Медаль", TextUtil.sanitize("<##F1C40F>Медаль"));
        assertEquals("Награда", TextUtil.sanitize("Награда</gold>"));
        assertEquals("<bold>Жирный</bold>", TextUtil.sanitize("<bold>Жирный</bold>"));
    }

    @Test
    void miniMessageStringsRenderCleanly() {
        assertEquals("Топор Дровосека",
                plain("<gradient:#C68B59:#F2D4A0><bold>Топор Дровосека</bold></gradient>"));
        assertEquals("▸ Алмаз ×5", plain("<#BB8CFF>▸ <#E6E6FA>Алмаз<reset> <dark_gray>×<white>5").replace("  ", " "));
    }

    @Test
    void humanizeFallback() {
        assertEquals("Diamond pickaxe", TextUtil.humanize("DIAMOND_PICKAXE"));
    }
}
