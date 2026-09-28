package twix.quest.reward;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Один предмет в награде: материал, количество, прочность, именование, энчанты. */
public final class RewardItem {

    public final String material;   // имя материала, например "WOODEN_AXE"
    public final int amount;
    public final String name;       // отображаемое имя (с MiniMessage)
    public final List<String> lore; // описания
    public final boolean unbreakable;
    public final List<Entry> enchantments; // список энчантов

    public RewardItem(String material, int amount, String name, List<String> lore, boolean unbreakable, List<Entry> enchantments) {
        this.material = material;
        this.amount = amount;
        this.name = name;
        this.lore = lore;
        this.unbreakable = unbreakable;
        this.enchantments = enchantments;
    }

    public static final class Entry {
        public final Enchantment enchantment;
        public final int level;
        public Entry(Enchantment e, int lvl) { this.enchantment = e; this.level = lvl; }
    }

    public ItemStack build() {
        Material mat = Material.matchMaterial(material.toUpperCase(Locale.ROOT));
        if (mat == null) mat = Material.BARRIER;
        ItemStack stack = new ItemStack(mat, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (name != null && !name.isEmpty()) {
                meta.displayName(twix.quest.util.TextUtil.mm(name));
            }
            if (lore != null && !lore.isEmpty()) {
                List<net.kyori.adventure.text.Component> comps = new ArrayList<>();
                for (String l : lore) comps.add(twix.quest.util.TextUtil.mm(l));
                meta.lore(comps);
            }
            if (unbreakable) meta.setUnbreakable(true);
            if (enchantments != null) {
                for (Entry e : enchantments) {
                    if (e.enchantment != null) meta.addEnchant(e.enchantment, e.level, true);
                }
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public String pretty() {
        StringBuilder sb = new StringBuilder();
        sb.append("⚒ ").append(amount).append("x ").append(material.toLowerCase(Locale.ROOT).replace('_', ' '));
        if (enchantments != null && !enchantments.isEmpty()) {
            sb.append(" <dark_gray>[");
            for (int i = 0; i < enchantments.size(); i++) {
                Entry e = enchantments.get(i);
                sb.append(e.enchantment.getKey().getKey()).append(" ").append(e.level);
                if (i < enchantments.size() - 1) sb.append(", ");
            }
            sb.append("]<reset>");
        }
        if (unbreakable) sb.append(" <red>неразрушим<reset>");
        return sb.toString();
    }
}
