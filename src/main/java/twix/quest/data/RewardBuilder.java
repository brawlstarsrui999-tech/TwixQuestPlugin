package twix.quest.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.java.JavaPlugin;
import twix.quest.reward.Reward;
import twix.quest.reward.RewardItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Конструктор наград из YAML-секции. */
final class RewardBuilder {
    private RewardBuilder() {}

    static Reward from(ConfigurationSection sec, JavaPlugin plugin) {
        if (sec == null) return new Reward(new ArrayList<>(), 0, 0, 0, new ArrayList<>(), "&rПоздравляю!");
        List<RewardItem> items = new ArrayList<>();
        if (sec.isList("items")) {
            for (Object o : sec.getList("items", new ArrayList<>())) {
                if (o instanceof ConfigurationSection itemSec) {
                    items.add(item(itemSec));
                } else if (o instanceof java.util.Map<?, ?> map) {
                    ConfigurationSection wrapSec = ConfigurationSerializationProxy.createSection(sec.getName(), map);
                    items.add(item(wrapSec));
                }
            }
        } else {
            // попробуем достать map-список
            for (ConfigurationSection itemSec : sec.getConfigurationSection("items") != null
                    ? sec.getConfigurationSection("items").getValues(false).values().stream()
                        .filter(v -> v instanceof ConfigurationSection)
                        .map(v -> (ConfigurationSection) v).toList()
                    : List.<ConfigurationSection>of()) {
                items.add(item(itemSec));
            }
        }
        int vault = sec.getInt("vault-coins", 0);
        int twix = sec.getInt("twix-coins", 0);
        int levels = sec.getInt("exp-levels", 0);
        List<String> cmds = sec.getStringList("commands");
        String msg = sec.getString("message", "&rПоздравляю! Квест пройден!");
        return new Reward(items, vault, twix, levels, cmds, msg);
    }

    private static RewardItem item(ConfigurationSection s) {
        String mat = s.getString("material", "STONE");
        int amount = s.getInt("amount", 1);
        String name = s.getString("name", null);
        List<String> lore = s.getStringList("lore");
        boolean ub = s.getBoolean("unbreakable", false);
        List<RewardItem.Entry> en = new ArrayList<>();
        ConfigurationSection ench = s.getConfigurationSection("enchantments");
        if (ench != null) {
            for (String k : ench.getKeys(false)) {
                Enchantment e = Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft(k.toLowerCase(Locale.ROOT)));
                if (e == null) continue;
                en.add(new RewardItem.Entry(e, ench.getInt(k, 1)));
            }
        }
        return new RewardItem(mat, amount, name, lore, ub, en);
    }

    /** Хелпер: оборачиваем Map в ConfigurationSection. */
    static final class ConfigurationSerializationProxy {
        static org.bukkit.configuration.ConfigurationSection createSection(String name, java.util.Map<?, ?> map) {
            org.bukkit.configuration.MemoryConfiguration mem = new org.bukkit.configuration.MemoryConfiguration();
            for (java.util.Map.Entry<?, ?> e : map.entrySet()) {
                mem.set(String.valueOf(e.getKey()), e.getValue());
            }
            return mem;
        }
    }
}
