package twix.quest.reward;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import twix.quest.util.TextUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Один предмет в награде: материал, количество, красивое имя, лор, чары.
 *
 * <p>Чары хранятся как ключи ({@code "sharpness"}), а в настоящий {@link Enchantment}
 * превращаются только при выдаче предмета — так разбор quests.yml не зависит
 * от запущенного сервера (и его можно проверять обычными тестами).</p>
 */
public final class RewardItem {

    public final Material material;
    public final int amount;
    /** Отображаемое имя (MiniMessage). {@code null} — ванильное название предмета. */
    public final String name;
    public final List<String> lore;
    public final boolean unbreakable;
    public final List<Entry> enchantments;
    /** Номер квеста — для подписи «Награда за квест #N» на именных предметах (0 — без подписи). */
    public final int questId;

    public RewardItem(Material material, int amount, String name, List<String> lore,
                      boolean unbreakable, List<Entry> enchantments, int questId) {
        this.material = material;
        this.amount = amount;
        this.name = name;
        this.lore = lore == null ? List.of() : lore;
        this.unbreakable = unbreakable;
        this.enchantments = enchantments == null ? List.of() : enchantments;
        this.questId = questId;
    }

    /** Чар: ключ без пространства имён и уровень. */
    public static final class Entry {
        public final String key;
        public final int level;

        public Entry(String key, int level) {
            this.key = key;
            this.level = level;
        }

        /** Настоящий чар из реестра сервера или {@code null}, если такого ключа нет. */
        public Enchantment resolve() {
            try {
                return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
            } catch (RuntimeException | LinkageError ex) {
                return null;
            }
        }
    }

    public boolean hasCustomName() {
        return name != null && !name.isEmpty();
    }

    // ------------------------------------------------------------------
    // Предметы
    // ------------------------------------------------------------------

    /** Один стак на {@code count} штук (количество может превышать максимум стака — режем в {@link #buildStacks()}). */
    private ItemStack build(int count) {
        ItemStack stack = new ItemStack(material, Math.max(1, count));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        if (hasCustomName()) {
            meta.displayName(TextUtil.mm(name));
        }
        List<String> loreLines = lore;
        if (loreLines.isEmpty() && hasCustomName() && questId > 0) {
            loreLines = List.of("<dark_gray>Награда за квест <#9B59FF>#" + questId);
        }
        if (!loreLines.isEmpty()) {
            List<Component> comps = new ArrayList<>();
            for (String l : loreLines) comps.add(TextUtil.mm(l));
            meta.lore(comps);
        }
        if (unbreakable) meta.setUnbreakable(true);

        for (Entry e : enchantments) {
            Enchantment ench = e.resolve();
            if (ench == null) {
                Logger.getLogger("TwixQuest").warning("Неизвестный чар '" + e.key + "' в награде квеста #" + questId);
                continue;
            }
            if (meta instanceof EnchantmentStorageMeta storage) {
                // Зачарованная книга хранит чары отдельно от обычных.
                storage.addStoredEnchant(ench, e.level, true);
            } else {
                meta.addEnchant(ench, e.level, true);
            }
        }
        stack.setItemMeta(meta);
        return stack;
    }

    /** Все стаки награды, разрезанные по максимальному размеру стака (книги и инструменты — по одному). */
    public List<ItemStack> buildStacks() {
        int max = Math.max(1, material.getMaxStackSize());
        List<ItemStack> out = new ArrayList<>();
        int left = Math.max(1, amount);
        while (left > 0) {
            int n = Math.min(max, left);
            out.add(build(n));
            left -= n;
        }
        return out;
    }

    /** Предмет для витрины в меню: ровно то, что получит игрок, плюс подпись «Награда». */
    public ItemStack preview() {
        ItemStack stack = build(Math.min(Math.max(1, amount), Math.max(1, material.getMaxStackSize())));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(TextUtil.mm("<dark_gray>─────────"));
            lore.add(TextUtil.mm("<#F1C40F>▸ Награда за квест"));
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    // ------------------------------------------------------------------
    // Текст
    // ------------------------------------------------------------------

    /** Название для текстов: своё (если задано) или ванильное на языке клиента. */
    public String label() {
        return hasCustomName() ? name : "<#E6E6FA>" + TextUtil.itemName(material);
    }

    /** Строка лора: «▸ Название ×N». */
    public String line() {
        String qty = amount > 1 ? " <dark_gray>×<white>" + amount : "";
        return "<#BB8CFF>▸ " + label() + "<reset>" + qty;
    }

    /** Подписи чаров («Острота II») на языке клиента. */
    public List<String> enchantLines() {
        List<String> out = new ArrayList<>();
        for (Entry e : enchantments) {
            Enchantment ench = null;
            int max = 0;
            try {
                ench = e.resolve();
                if (ench != null) max = ench.getMaxLevel();
            } catch (RuntimeException | LinkageError ignored) {
                // без сервера уровень покажем как есть
            }
            out.add("<#A9A9FF>    " + TextUtil.enchantName(e.key, e.level, max));
        }
        return out;
    }
}
