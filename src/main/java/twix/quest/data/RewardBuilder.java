package twix.quest.data;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import twix.quest.reward.Reward;
import twix.quest.reward.RewardItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Конструктор наград из YAML-секции {@code reward}.
 *
 * <p><b>Почему здесь работа с Map, а не с ConfigurationSection.</b> Список
 * {@code items: [ {...}, {...} ]} Bukkit отдаёт как {@code List<Map>}, а не как секции:
 * вложенный блок {@code enchantments} внутри такого элемента остаётся обычной
 * {@code LinkedHashMap}. Прежняя версия читала его через
 * {@code getConfigurationSection("enchantments")}, получала {@code null} и молча
 * выдавала предметы БЕЗ чар. Теперь оба представления (Map и секция) поддерживаются.</p>
 */
final class RewardBuilder {
    private RewardBuilder() {}

    static Reward from(ConfigurationSection sec, JavaPlugin plugin, int questId) {
        Logger log = plugin != null ? plugin.getLogger() : Logger.getLogger("TwixQuest");
        if (sec == null) return new Reward(new ArrayList<>(), 0, 0, 0, new ArrayList<>(), "");

        List<RewardItem> items = new ArrayList<>();
        Object raw = sec.get("items");
        if (raw instanceof List<?> list) {
            for (Object o : list) addItem(items, asMap(o), questId, log);
        } else if (raw instanceof ConfigurationSection cs) {
            for (String k : cs.getKeys(false)) addItem(items, asMap(cs.get(k)), questId, log);
        }

        int vault = sec.getInt("vault-coins", 0);
        int twix = sec.getInt("twix-coins", 0);
        int levels = sec.getInt("exp-levels", 0);
        List<String> cmds = sec.getStringList("commands");
        String msg = sec.getString("message", "");
        return new Reward(items, vault, twix, levels, cmds, msg);
    }

    private static void addItem(List<RewardItem> out, Map<String, Object> m, int questId, Logger log) {
        if (m == null) return;
        String matName = String.valueOf(m.getOrDefault("material", "STONE"));
        Material mat = Material.matchMaterial(matName.trim().toUpperCase(Locale.ROOT));
        if (mat == null || mat.isAir()) {
            log.warning("Квест #" + questId + ": неизвестный материал награды '" + matName + "' — предмет пропущен.");
            return;
        }
        int amount = Math.max(1, intOf(m.get("amount"), 1));
        Object nameObj = m.get("name");
        String name = nameObj == null ? null : String.valueOf(nameObj);

        List<String> lore = new ArrayList<>();
        if (m.get("lore") instanceof List<?> l) {
            for (Object o : l) lore.add(String.valueOf(o));
        }
        boolean unbreakable = Boolean.parseBoolean(String.valueOf(m.getOrDefault("unbreakable", false)));

        List<RewardItem.Entry> ench = new ArrayList<>();
        Map<String, Object> enchMap = asMap(m.get("enchantments"));
        if (enchMap != null) {
            for (Map.Entry<String, Object> e : enchMap.entrySet()) {
                int level = intOf(e.getValue(), 1);
                if (level <= 0) continue;
                ench.add(new RewardItem.Entry(normalizeKey(e.getKey()), level));
            }
        }
        out.add(new RewardItem(mat, amount, name, lore, unbreakable, ench, questId));
    }

    /** {@code "minecraft:Sharpness"} → {@code "sharpness"}. */
    static String normalizeKey(String key) {
        String k = key.trim().toLowerCase(Locale.ROOT);
        return k.startsWith("minecraft:") ? k.substring("minecraft:".length()) : k;
    }

    /** Приводит к Map и секцию, и сырую Map (см. описание класса). */
    static Map<String, Object> asMap(Object o) {
        if (o instanceof ConfigurationSection cs) {
            return new LinkedHashMap<>(cs.getValues(false));
        }
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) out.put(String.valueOf(e.getKey()), e.getValue());
            return out;
        }
        return null;
    }

    private static int intOf(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        if (o != null) {
            try {
                return Integer.parseInt(String.valueOf(o).trim());
            } catch (NumberFormatException ignored) {
                // не число — берём значение по умолчанию
            }
        }
        return def;
    }
}
