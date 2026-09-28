package twix.quest.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import twix.quest.TwixQuestPlugin;
import twix.quest.data.PlayerQuestData;
import twix.quest.data.QuestRegistry;
import twix.quest.quest.QuestDefinition;
import twix.quest.util.TextUtil;

import java.util.*;
import java.util.concurrent.TimeUnit;

/** Менеджер инвентарных меню. */
public final class MenuManager {

    public enum MenuKind { MAIN, TREE, CLANS, TOP, PROFILE, REWARD_INFO }

    private final JavaPlugin plugin;
    private final Map<UUID, MenuState> states = new HashMap<>();

    private static final String UI_TITLE_MAIN = "「TwixQuest」│ Главное меню";
    private static final String UI_TITLE_TREE = "「TwixQuest」│ Дерево квестов";
    private static final String UI_TITLE_CLAN = "「TwixQuest」│ Клановые квесты";
    private static final String UI_TITLE_TOP  = "「TwixQuest」│ Топ скорости";
    private static final String UI_TITLE_PROF = "「TwixQuest」│ Профиль";
    private static final String UI_TITLE_RW   = "「TwixQuest」│ Награда";

    public MenuManager(JavaPlugin plugin) { this.plugin = plugin; }

    public void openMain(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, UI_TITLE_MAIN);
        decorate(inv, 0, 1, 2, 3, 5, 6, 7, 8);
        decorate(inv, 18, 19, 20, 21, 23, 24, 25, 26);

        inv.setItem(11, button(Material.SPRUCE_SIGN, "<b><color:#9B59FF>Дерево квестов</color></b>",
                List.of("<gray>Полная история основных",
                        "<gray>квестов TwixRPG.",
                        "",
                        "<color:#D3A8FF>ЛКМ — открыть"), false));
        inv.setItem(13, button(Material.EMERALD, "<b><color:#9B59FF>Топ скорости</color></b>",
                List.of("<gray>Тройка самых быстрых",
                        "<gray>путешественников по квестам.",
                        "",
                        "<color:#D3A8FF>ЛКМ — открыть"), false));
        inv.setItem(15, button(Material.PURPLE_BANNER, "<b><color:#9B59FF>Клановые квесты</color></b>",
                List.of("<gray>Доступно только для",
                        "<gray>участников клана.", "",
                        "<red>WIP"), true));
        inv.setItem(22, button(Material.PLAYER_HEAD, "<b><color:#9B59FF>Профиль игрока</color></b>",
                List.of("<gray>Здесь собрана вся",
                        "<gray>информация о Ваших квестах.",
                        "",
                        "<color:#D3A8FF>ЛКМ — открыть"), false));

        states.put(p.getUniqueId(), new MenuState(MenuKind.MAIN, 27));
        p.openInventory(inv);
    }

    public void openTree(Player p) {
        TwixQuestPlugin tp = TwixQuestPlugin.inst();
        PlayerQuestData data = tp.getPlayerData();
        List<QuestDefinition> all = tp.getQuestRegistry().all();

        // Раскладка: 3 колонки по 6 строк = 18 квестов на минимальный инвентарь (27 слотов).
        int slots = Math.min(54, Math.max(27, ((all.size() + 8) / 9 + 1) * 9));
        Inventory inv = Bukkit.createInventory(null, slots, UI_TITLE_TREE);
        UUID uuid = p.getUniqueId();

        // Заполняем внутренние слоты (не граница и не нижняя панель) последовательно.
        // Сначала границы, потом квесты слева направо, сверху вниз.
        java.util.List<Integer> availableSlots = new java.util.ArrayList<>();
        for (int i = 0; i < slots; i++) {
            int row = i / 9;       // 0..5
            int col = i % 9;       // 0..8
            if (row == 0 || row == slots / 9 - 1) continue;
            if (col == 0 || col == 8) continue;
            availableSlots.add(i);
        }

        for (int i = 0; i < Math.min(all.size(), availableSlots.size()); i++) {
            QuestDefinition q = all.get(i);
            int slot = availableSlots.get(i);
            boolean completed = data.isCompleted(uuid, q.id);
            boolean active = tp.getQuestManager().currentQuest(uuid) != null
                    && tp.getQuestManager().currentQuest(uuid).id == q.id;
            boolean future = !completed && !active;

            Material mat = future ? q.icon().getType()
                    : (completed ? Material.LIME_STAINED_GLASS_PANE : Material.PURPLE_STAINED_GLASS_PANE);

            ItemStack stack = new ItemStack(mat);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.displayName(decorateName(q, completed, active, future));
                meta.lore(buildLore(p, q, completed, active, future));
                meta.addItemFlags(ItemFlag.values());
                stack.setItemMeta(meta);
            }
            inv.setItem(slot, stack);
        }

        decorate(inv, 0, 1, 2, 3, 4, 5, 6, 7, 8);
        decorate(inv, slots - 9, slots - 8, slots - 7, slots - 6, slots - 5, slots - 4, slots - 3, slots - 2, slots - 1);
        inv.setItem(slots - 5, button(Material.ARROW, "<b><color:#9B59FF>Назад</color></b>",
                List.of("<color:#D3A8FF>В главное меню", "<dark_gray>→"), false));

        states.put(p.getUniqueId(), new MenuState(MenuKind.TREE, slots));
        p.openInventory(inv);
    }

    public void openClans(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, UI_TITLE_CLAN);
        decorate(inv, 0, 1, 2, 3, 5, 6, 7, 8);
        decorate(inv, 18, 19, 20, 21, 23, 24, 25, 26);
        inv.setItem(13, button(Material.BARRIER, "<b><color:#9B59FF>WIP</color></b>",
                List.of("<gray>Клановые квесты находятся",
                        "<gray>в разработке.", "",
                        "<color:#D3A8FF>Скоро..."), false));
        inv.setItem(22, button(Material.ARROW, "<b><color:#9B59FF>Назад</color></b>",
                List.of("<color:#D3A8FF>В главное меню"), false));
        states.put(p.getUniqueId(), new MenuState(MenuKind.CLANS, 27));
        p.openInventory(inv);
    }

    public void openTop(Player p) {
        TwixQuestPlugin tp = TwixQuestPlugin.inst();
        List<PlayerQuestData.SpeedEntry> top = tp.getPlayerData().getSpeedTop(tp.getQuestRegistry(), 3);
        Inventory inv = Bukkit.createInventory(null, 27, UI_TITLE_TOP);
        decorate(inv, 0, 1, 2, 3, 5, 6, 7, 8);
        decorate(inv, 18, 19, 20, 21, 23, 24, 25, 26);

        Material[] medals = { Material.GOLD_INGOT, Material.IRON_INGOT, Material.COPPER_INGOT };
        for (int i = 0; i < 3; i++) {
            ItemStack stack = new ItemStack(medals[i]);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                if (i < top.size()) {
                    PlayerQuestData.SpeedEntry e = top.get(i);
                    meta.displayName(TextUtil.mm("<b><color:#F1C40F>#" + (i + 1) + " " + e.name() + "</color></b>"));
                    meta.lore(List.of(TextUtil.mm("<gray>Прошёл квесты за <color:#D3A8FF>" + formatDuration(e.durationMs()))));
                } else {
                    meta.displayName(TextUtil.mm("<b><color:#F1C40F>#" + (i + 1) + " —</color></b>"));
                    meta.lore(List.of(TextUtil.mm("<dark_gray>Здесь пока никого")));
                }
                meta.addItemFlags(ItemFlag.values());
                stack.setItemMeta(meta);
            }
            inv.setItem(11 + i * 2, stack);
        }
        inv.setItem(22, button(Material.ARROW, "<b><color:#9B59FF>Назад</color></b>",
                List.of("<color:#D3A8FF>В главное меню"), false));
        states.put(p.getUniqueId(), new MenuState(MenuKind.TOP, 27));
        p.openInventory(inv);
    }

    public void openProfile(Player p) {
        TwixQuestPlugin tp = TwixQuestPlugin.inst();
        UUID id = p.getUniqueId();
        QuestRegistry reg = tp.getQuestRegistry();
        int done = tp.getPlayerData().countMainCompleted(id, reg);
        int total = reg.size();

        Inventory inv = Bukkit.createInventory(null, 27, UI_TITLE_PROF);
        decorate(inv, 0, 1, 2, 3, 5, 6, 7, 8);
        decorate(inv, 18, 19, 20, 21, 23, 24, 25, 26);

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta hm = head.getItemMeta();
        if (hm != null) {
            hm.displayName(TextUtil.mm("<b><color:#9B59FF>" + p.getName() + "</color></b>"));
            long started = tp.getPlayerData().startTime(id);
            long playtime = System.currentTimeMillis() - started;
            hm.lore(List.of(
                    TextUtil.mm("<gray>Время с начала квестов:"),
                    TextUtil.mm("<color:#D3A8FF>" + formatDuration(playtime)),
                    TextUtil.mm(""),
                    TextUtil.mm("<gray>Основные квесты: <color:#F1C40F>" + done + "/" + total),
                    TextUtil.mm("<gray>Клановые квесты: <color:#F1C40F>0<color:#9B59FF>/<color:#F1C40F>0 <dark_gray>(WIP)")
            ));
            hm.addItemFlags(ItemFlag.values());
            head.setItemMeta(hm);
        }
        inv.setItem(13, head);
        inv.setItem(22, button(Material.ARROW, "<b><color:#9B59FF>Назад</color></b>",
                List.of("<color:#D3A8FF>В главное меню"), false));
        states.put(p.getUniqueId(), new MenuState(MenuKind.PROFILE, 27));
        p.openInventory(inv);
    }

    private void showRewardInfo(Player p, QuestDefinition q) {
        Inventory inv = Bukkit.createInventory(null, 27, UI_TITLE_RW);
        decorate(inv, 0, 1, 2, 3, 5, 6, 7, 8);
        decorate(inv, 18, 19, 20, 21, 23, 24, 25, 26);

        ItemStack icon = q.icon();
        ItemMeta m = icon.getItemMeta();
        if (m != null) {
            m.displayName(TextUtil.mm("<b><color:#9B59FF>" + q.name + "</color></b>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.mm("<dark_gray>────────────"));
            lore.add(TextUtil.mm("<gray>Условие:"));
            lore.add(TextUtil.mm("<color:#D3A8FF>" + q.description));
            lore.add(TextUtil.mm("<dark_gray>────────────"));
            lore.add(TextUtil.mm("<gray>Награда:"));
            lore.add(TextUtil.mm(q.reward.describe()));
            m.lore(lore);
            m.addItemFlags(ItemFlag.values());
            icon.setItemMeta(m);
        }
        inv.setItem(13, icon);
        inv.setItem(22, button(Material.ARROW, "<b><color:#9B59FF>Назад</color></b>",
                List.of("<color:#D3A8FF>К дереву квестов"), false));
        states.put(p.getUniqueId(), new MenuState(MenuKind.REWARD_INFO, 27));
        p.openInventory(inv);
    }

    public boolean handleClick(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        MenuState st = states.get(p.getUniqueId());
        if (st == null) return false;
        e.setCancelled(true);

        int slot = e.getRawSlot();
        if (slot < 0) return true;

        switch (st.kind) {
            case MAIN -> {
                if (slot == 11) openTree(p);
                else if (slot == 13) openTop(p);
                else if (slot == 15) openClans(p);
                else if (slot == 22) openProfile(p);
            }
            case TREE -> {
                Inventory top = e.getInventory();
                int size = top.getSize();
                if (slot == size - 5) { openMain(p); break; }
                ItemStack clicked = top.getItem(slot);
                if (clicked != null && clicked.getType() != Material.GRAY_STAINED_GLASS_PANE) {
                    TwixQuestPlugin tp = TwixQuestPlugin.inst();
                    for (QuestDefinition q : tp.getQuestRegistry().all()) {
                        ItemMeta cm = clicked.getItemMeta();
                        if (cm != null && cm.displayName() != null) {
                            String plain = MiniMessage.miniMessage().serialize(cm.displayName());
                            if (plain.contains("#" + q.id + " ")) {
                                showRewardInfo(p, q);
                                break;
                            }
                        }
                    }
                }
            }
            case CLANS -> { if (slot == 22) openMain(p); }
            case TOP   -> { if (slot == 22) openMain(p); }
            case PROFILE -> { if (slot == 22) openMain(p); }
            case REWARD_INFO -> { openTree(p); }
        }
        return true;
    }

    public void handleClose(InventoryCloseEvent e) {
        states.remove(e.getPlayer().getUniqueId());
    }

    // helpers

    private ItemStack button(Material mat, String name, List<String> lore, boolean glow) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.mm(name));
            List<Component> comps = new ArrayList<>();
            for (String l : lore) comps.add(TextUtil.mm(l));
            meta.lore(comps);
            meta.addItemFlags(ItemFlag.values());
            stack.setItemMeta(meta);
        }
        if (glow) addGlow(stack);
        return stack;
    }

    private void addGlow(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        if (meta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta esm) {
            esm.addStoredEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            stack.setItemMeta(esm);
        } else {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            stack.setItemMeta(meta);
        }
    }

    private void decorate(Inventory inv, int... slots) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta();
        if (pm != null) { pm.displayName(Component.empty()); pane.setItemMeta(pm); }
        for (int s : slots) if (inv.getSize() > s) inv.setItem(s, pane);
    }

    private Component decorateName(QuestDefinition q, boolean completed, boolean active, boolean future) {
        String prefix = completed ? "<b><color:#27AE60>✓</color></b> " :
                active ? "<b><color:#D3A8FF>●</color></b> " : "<b><color:#4B0082>?</color></b> ";
        String idColor = completed ? "<color:#27AE60>" : active ? "<color:#9B59FF>" : "<color:#4B0082>";
        return TextUtil.mm(prefix + idColor + "#" + q.id + " " + q.name + "</color>");
    }

    private List<Component> buildLore(Player p, QuestDefinition q, boolean completed, boolean active, boolean future) {
        List<Component> list = new ArrayList<>();
        list.add(TextUtil.mm("<dark_gray>────────────"));
        list.add(TextUtil.mm("<gray>" + q.shortType()));
        list.add(TextUtil.mm("<color:#D3A8FF>" + q.description));
        if (completed) {
            list.add(TextUtil.mm("<dark_gray>────────────"));
            list.add(TextUtil.mm("<color:#27AE60><b>✓ Завершено</b></color>"));
        } else if (active) {
            int prog = TwixQuestPlugin.inst().getQuestManager().currentProgress(p.getUniqueId(), q);
            list.add(TextUtil.mm("<dark_gray>────────────"));
            list.add(TextUtil.mm("<gray>Прогресс: <color:#F1C40F>" + prog + "/" + q.amount + "</color>"));
            list.add(TextUtil.mm("<gray>Награда:"));
            list.add(TextUtil.mm(q.reward.describe()));
        } else {
            list.add(TextUtil.mm("<dark_gray>────────────"));
            list.add(TextUtil.mm("<color:#4B0082>Не доступно. Завершите предыдущие квесты."));
        }
        list.add(TextUtil.mm("<dark_gray>────────────"));
        return list;
    }

    private String formatDuration(long ms) {
        long m = TimeUnit.MILLISECONDS.toMinutes(ms);
        long h = m / 60;
        long mm = m % 60;
        long s = (TimeUnit.MILLISECONDS.toSeconds(ms) % 60);
        if (h > 0) return h + " ч " + mm + " мин";
        if (mm > 0) return mm + " мин " + s + " сек";
        return s + " сек";
    }

    private static final class MenuState {
        final MenuKind kind;
        final int size;
        MenuState(MenuKind k, int size) { this.kind = k; this.size = size; }
    }
}
