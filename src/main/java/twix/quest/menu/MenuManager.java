package twix.quest.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import twix.quest.TwixQuestPlugin;
import twix.quest.data.PlayerQuestData;
import twix.quest.data.QuestRegistry;
import twix.quest.manager.QuestManager;
import twix.quest.quest.QuestDefinition;
import twix.quest.reward.RewardItem;
import twix.quest.util.TextUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Менеджер инвентарных меню.
 *
 * Оформление: фиолетовая палитра (#9B59FF / #D3A8FF / #BB8CFF),
 * градиенты MiniMessage, hover-описания. Будущие квесты закрыты
 * железным блоком с замком. Клик по предметам в меню запрещён —
 * кнопки невозможно «украсть».
 */
public final class MenuManager {

    public enum MenuKind { MAIN, TREE, CLANS, TOP, PROFILE, QUEST_INFO }

    private final JavaPlugin plugin;
    private final Map<UUID, MenuState> states = new HashMap<>();

    // Готовые Component-заголовки (MiniMessage уже распарсен)
    private static final net.kyori.adventure.text.Component TITLE_MAIN =
            TextUtil.mm("<gradient:#BB8CFF:#9B59FF><bold>TwixQuest</bold></gradient> <dark_gray>· <#D3A8FF>Главное меню");
    private static final net.kyori.adventure.text.Component TITLE_TREE =
            TextUtil.mm("<gradient:#BB8CFF:#9B59FF><bold>TwixQuest</bold></gradient> <dark_gray>· <#D3A8FF>Дерево квестов");
    private static final net.kyori.adventure.text.Component TITLE_CLAN =
            TextUtil.mm("<gradient:#BB8CFF:#9B59FF><bold>TwixQuest</bold></gradient> <dark_gray>· <#D3A8FF>Клановые квесты");
    private static final net.kyori.adventure.text.Component TITLE_TOP =
            TextUtil.mm("<gradient:#BB8CFF:#9B59FF><bold>TwixQuest</bold></gradient> <dark_gray>· <#D3A8FF>Топ игроков");
    private static final net.kyori.adventure.text.Component TITLE_PROF =
            TextUtil.mm("<gradient:#BB8CFF:#9B59FF><bold>TwixQuest</bold></gradient> <dark_gray>· <#D3A8FF>Профиль");
    private static final net.kyori.adventure.text.Component TITLE_INFO =
            TextUtil.mm("<gradient:#BB8CFF:#9B59FF><bold>TwixQuest</bold></gradient> <dark_gray>· <#D3A8FF>Информация о квесте");

    public MenuManager(JavaPlugin plugin) { this.plugin = plugin; }

    /**
     * Сравнивает инвентарь клика с тем, что мы открыли игроку.
     * Сравниваем по identity (==) — Bukkit Inventory.equals может вернуть true
     * для разных custom inventories с null holder.
     */
    private boolean isOurMenu(Player p, Inventory clickedTop) {
        if (clickedTop == null) return false;
        MenuState st = states.get(p.getUniqueId());
        if (st == null) return false;
        return clickedTop == st.inventory;
    }

    // ------------------------------------------------------------------
    // Главное меню
    // ------------------------------------------------------------------
    public void openMain(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_MAIN);

        // Декоративная рамка — обходим слот 4 (там заголовок) и слот 9-17 (центральный ряд),
        // слоты 22 (Профиль) и 11/13/15 — кнопки
        frameFill(inv, Material.PURPLE_STAINED_GLASS_PANE, 0, 1, 2, 3, 5, 6, 7, 8);
        frameFill(inv, Material.PURPLE_STAINED_GLASS_PANE, 18, 19, 20, 21, 23, 24, 25, 26);

        // Заголовок по центру верха — игровой айтем с подсказкой
        inv.setItem(4, button(Material.NETHER_STAR,
                "<gradient:#BB8CFF:#9B59FF><bold>TwixQuest</bold></gradient>",
                List.of(
                        "<dark_gray>─────────",
                        "<gray>Плагин квестов сервера",
                        "<gray><italic>TwixRPG</italic>",
                        "<dark_gray>─────────",
                        "<#D3A8FF>Здесь собраны все ваши",
                        "<#D3A8FF>основные задания и награды."
                ),
                false));

        // 4 основных кнопки
        inv.setItem(11, bigButton(Material.SPRUCE_SIGN,
                "<#9B59FF><bold>Дерево квестов</bold>",
                List.of(
                        "<dark_gray>─────────",
                        "<gray>Полная история основных",
                        "<gray>квестов TwixRPG.",
                        "<dark_gray>─────────",
                        "<#D3A8FF>ЛКМ <dark_gray>— <gray>открыть"
                )));
        inv.setItem(13, bigButton(Material.EMERALD,
                "<#9B59FF><bold>Топ игроков</bold>",
                List.of(
                        "<dark_gray>─────────",
                        "<gray>Кто прошёл больше всего",
                        "<gray>квестов — и быстрее всех.",
                        "<dark_gray>─────────",
                        "<#D3A8FF>ЛКМ <dark_gray>— <gray>открыть"
                )));
        inv.setItem(15, bigButton(Material.PURPLE_BANNER,
                "<#9B59FF><bold>Клановые квесты</bold>",
                List.of(
                        "<dark_gray>─────────",
                        "<gray>Доступно только для",
                        "<gray>участников клана.",
                        "<dark_gray>─────────",
                        "<red><bold>WIP <dark_gray>— <gray>в разработке"
                )));
        inv.setItem(22, bigButton(Material.PLAYER_HEAD,
                "<#9B59FF><bold>Профиль игрока</bold>",
                List.of(
                        "<dark_gray>─────────",
                        "<gray>Здесь собрана вся",
                        "<gray>информация о ваших",
                        "<gray>квестах.",
                        "<dark_gray>─────────",
                        "<#D3A8FF>ЛКМ <dark_gray>— <gray>открыть"
                )));

        states.put(p.getUniqueId(), new MenuState(MenuKind.MAIN, 27, inv));
        p.openInventory(inv);
    }

    // ------------------------------------------------------------------
    // Дерево квестов
    // ------------------------------------------------------------------

    /** Квестов на странице: строки 1-4 инвентаря (строки 0 и 5 — рамка и навигация). */
    private static final int TREE_PAGE_SIZE = 36;
    private static final String SEP = "<dark_gray>─────────";
    /** Ширина строки описания в подсказке (в символах). */
    private static final int WRAP = 34;

    /** Открывает дерево на странице, где находится текущий квест игрока. */
    public void openTree(Player p) {
        TwixQuestPlugin tp = TwixQuestPlugin.inst();
        QuestRegistry reg = tp.getQuestRegistry();
        QuestDefinition active = tp.getQuestManager().currentQuest(p.getUniqueId());
        int page;
        if (active != null) {
            page = Math.max(0, reg.indexOf(active.id)) / TREE_PAGE_SIZE;
        } else {
            page = Math.max(0, reg.size() - 1) / TREE_PAGE_SIZE; // всё пройдено — последняя страница
        }
        openTree(p, page);
    }

    public void openTree(Player p, int page) {
        QuestRegistry reg = TwixQuestPlugin.inst().getQuestRegistry();
        int total = reg.size();
        int pages = Math.max(1, (total + TREE_PAGE_SIZE - 1) / TREE_PAGE_SIZE);
        int pg = Math.max(0, Math.min(page, pages - 1));
        int onPage = Math.max(0, Math.min(TREE_PAGE_SIZE, total - pg * TREE_PAGE_SIZE));
        int rows = Math.max(1, (onPage + 8) / 9);
        int size = (rows + 2) * 9; // рамка сверху и навигация снизу

        Inventory inv = Bukkit.createInventory(null, size, TITLE_TREE);
        MenuState st = new MenuState(MenuKind.TREE, size, inv);
        st.page = pg;
        st.pages = pages;
        fillTree(p, st);
        states.put(p.getUniqueId(), st);
        p.openInventory(inv);
    }

    /** Перерисовывает дерево прямо в открытом окне (без закрытия — курсор не сбрасывается). */
    private void fillTree(Player p, MenuState st) {
        TwixQuestPlugin tp = TwixQuestPlugin.inst();
        PlayerQuestData data = tp.getPlayerData();
        QuestRegistry reg = tp.getQuestRegistry();
        UUID uuid = p.getUniqueId();
        Inventory inv = st.inventory;
        inv.clear();
        st.slotQuest.clear();

        List<QuestDefinition> all = reg.all();
        QuestDefinition active = tp.getQuestManager().currentQuest(uuid);
        int start = st.page * TREE_PAGE_SIZE;
        int onPage = Math.max(0, Math.min(TREE_PAGE_SIZE, all.size() - start));

        for (int i = 0; i < onPage; i++) {
            QuestDefinition q = all.get(start + i);
            int slot = 9 + i;
            boolean completed = data.isCompleted(uuid, q.id);
            boolean isActive = active != null && active.id == q.id;
            ItemStack stack;
            if (completed) stack = completedQuestIcon(p, q);
            else if (isActive) stack = activeQuestIcon(p, q);
            else stack = lockedQuestIcon(q.id);
            inv.setItem(slot, stack);
            // Квест определяем по слоту, а не по тексту названия: раньше «Квест #1»
            // совпадал с «Квест #12», и вместо нужного открывался квест про дуб.
            st.slotQuest.put(slot, q.id);
        }

        inv.setItem(4, treeHeader(data.countMainCompleted(uuid, reg), all.size(), st.page, st.pages));

        int size = st.size;
        inv.setItem(size - 5, backButton());
        if (st.page > 0) inv.setItem(size - 6, pageButton(false, st.page, st.pages));
        if (st.page < st.pages - 1) inv.setItem(size - 4, pageButton(true, st.page, st.pages));

        for (int s = 0; s < size; s++) {
            if (inv.getItem(s) == null) frameFill(inv, Material.PURPLE_STAINED_GLASS_PANE, s);
        }
    }

    private ItemStack treeHeader(int done, int total, int page, int pages) {
        ItemStack stack = new ItemStack(Material.BOOK);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.mm("<gradient:#BB8CFF:#9B59FF><bold>Дерево квестов</bold></gradient>"));
            List<String> lore = new ArrayList<>();
            lore.add(SEP);
            lore.add("<gray>Выполнено: <#F1C40F>" + done + "<gray>/<#F1C40F>" + total);
            lore.add(TextUtil.bar(done, total, 10));
            if (pages > 1) lore.add("<gray>Страница: <#D3A8FF>" + (page + 1) + "<gray>/<#D3A8FF>" + pages);
            lore.add(SEP);
            lore.add("<#D3A8FF>Квесты проходятся по порядку.");
            lore.add("<#D3A8FF>Следующий откроется после текущего.");
            meta.lore(components(lore));
            meta.addItemFlags(ItemFlag.values());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack pageButton(boolean next, int page, int pages) {
        ItemStack stack = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.mm(next ? "<#9B59FF><bold>Дальше →" : "<#9B59FF><bold>← Назад по списку"));
            meta.lore(components(List.of(SEP,
                    "<gray>Страница <#D3A8FF>" + (next ? page + 2 : page) + "<gray>/<#D3A8FF>" + pages)));
            meta.addItemFlags(ItemFlag.values());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack lockedQuestIcon(int id) {
        // Будущий квест — железный блок, загадочное оформление
        ItemStack stack = new ItemStack(Material.IRON_BLOCK);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.mm("<dark_gray><bold>???"));
            meta.lore(components(List.of(
                    SEP,
                    "<dark_gray>Квест <gray>#" + id,
                    SEP,
                    "<dark_gray><italic>Завершите предыдущие",
                    "<dark_gray><italic>квесты, чтобы открыть.")));
            meta.addItemFlags(ItemFlag.values());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack completedQuestIcon(Player p, QuestDefinition q) {
        ItemStack stack = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.mm("<#27AE60><bold>✓ <#BB8CFF>" + q.name));
            List<String> lore = new ArrayList<>();
            lore.add(SEP);
            lore.add("<gray>#" + q.id + " <dark_gray>· <#27AE60><bold>Завершено");
            lore.add(SEP);
            lore.addAll(wrap(q.description, "<#D3A8FF>"));
            lore.add(SEP);
            lore.add("<gray>Награда:");
            lore.addAll(q.reward.lines());
            lore.add(SEP);
            lore.add("<#D3A8FF><bold>ЛКМ <dark_gray>— <gray>информация о квесте");
            meta.lore(components(lore));
            meta.addItemFlags(ItemFlag.values());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack activeQuestIcon(Player p, QuestDefinition q) {
        QuestManager qm = TwixQuestPlugin.inst().getQuestManager();
        ItemStack stack = new ItemStack(q.iconMaterial());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.mm("<#D3A8FF><bold>● <#9B59FF>Квест #" + q.id + " <dark_gray>· <#D3A8FF>" + q.name));
            List<String> lore = new ArrayList<>();
            lore.add(SEP);
            lore.add("<gray>Тип: <#D3A8FF>" + q.shortType());
            lore.addAll(wrap(q.description, "<#D3A8FF>"));
            lore.add(SEP);
            lore.addAll(progressLines(p, q, qm));
            lore.add(SEP);
            lore.add("<#F1C40F><bold>Награда:");
            lore.addAll(q.reward.lines());
            lore.add(SEP);
            if (q.isClaimable() && q.consume) {
                lore.add("<#27AE60><bold>ЛКМ <dark_gray>— <gray>вложить предметы");
            } else if (q.isClaimable()) {
                lore.add("<#27AE60><bold>ЛКМ <dark_gray>— <gray>засчитать (если предмет уже у вас)");
            }
            lore.add("<#D3A8FF><bold>ПКМ <dark_gray>— <gray>информация о квесте");
            meta.lore(components(lore));
            meta.addItemFlags(ItemFlag.values());
            meta.setEnchantmentGlintOverride(true);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /**
     * Строки прогресса. Для вложений — по каждому предмету («вложено/нужно» и сколько есть
     * в сумке), для остальных квестов — общий счётчик с полоской.
     */
    private List<String> progressLines(Player p, QuestDefinition q, QuestManager qm) {
        UUID id = p.getUniqueId();
        List<String> lines = new ArrayList<>();
        if (q.isClaimable() && q.consume) {
            lines.add("<gray>Нужно вложить:");
            for (QuestDefinition.MaterialRequirement r : q.requirements()) {
                int dep = Math.min(r.amount, qm.deposited(id, q, r.material));
                boolean full = dep >= r.amount;
                String line = (full ? "<#27AE60>  ✔ " : "<#BB8CFF>  ▸ ") + "<#E6E6FA>" + TextUtil.itemName(r.material)
                        + " <gray>" + dep + "<dark_gray>/<#F1C40F>" + r.amount;
                if (!full) {
                    int bag = qm.countInInventory(p, r.material, true);
                    line += " <dark_gray>(в сумке: " + (bag >= r.amount - dep ? "<#27AE60>" : "<#E74C3C>") + bag + "<dark_gray>)";
                }
                lines.add(line);
            }
            int done = qm.currentProgress(id, q);
            lines.add(TextUtil.bar(done, q.totalAmount(), 12));
        } else if (q.isClaimable()) {
            lines.add("<gray>Нужно скрафтить:");
            for (QuestDefinition.MaterialRequirement r : q.requirements()) {
                int bag = qm.countInInventory(p, r.material, false);
                lines.add("<#BB8CFF>  ▸ <#E6E6FA>" + TextUtil.itemName(r.material) + (r.amount > 1 ? " <white>×" + r.amount : "")
                        + " <dark_gray>(в сумке: " + (bag >= r.amount ? "<#27AE60>" : "<#E74C3C>") + bag + "<dark_gray>)");
            }
        } else {
            int done = qm.currentProgress(id, q);
            lines.add("<gray>Прогресс: <#F1C40F>" + done + "<gray>/<#F1C40F>" + q.amount);
            lines.add(TextUtil.bar(done, q.amount, 12));
        }
        return lines;
    }

    // ------------------------------------------------------------------
    // Клановые квесты (WIP)
    // ------------------------------------------------------------------
    public void openClans(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_CLAN);
        frameFill(inv, Material.PURPLE_STAINED_GLASS_PANE, 0, 1, 2, 3, 5, 6, 7, 8,
                18, 19, 20, 21, 23, 24, 25, 26);
        inv.setItem(13, bigButton(Material.BARRIER,
                "<red><bold>WIP",
                List.of(
                        "<dark_gray>─────────",
                        "<gray>Клановые квесты находятся",
                        "<gray>в активной разработке.",
                        "<dark_gray>─────────",
                        "<#D3A8FF>Скоро здесь появятся",
                        "<#D3A8FF>общие задания для кланов!"
                )));
        inv.setItem(22, backButton());
        states.put(p.getUniqueId(), new MenuState(MenuKind.CLANS, 27, inv));
        p.openInventory(inv);
    }

    // ------------------------------------------------------------------
    // Топ игроков
    // ------------------------------------------------------------------

    /** HEX-цвета медалей БЕЗ решётки: тег собирается как "<#" + hex + ">". */
    private static final String[] MEDAL_HEX = { "F1C40F", "BDC3C7", "CD7F32" };
    private static final Material[] MEDAL_ITEM = { Material.GOLD_INGOT, Material.IRON_INGOT, Material.COPPER_INGOT };
    /** Слоты 1-3 мест (средний ряд) и 4-9 мест (нижний ряд, вокруг кнопки «Назад»). */
    private static final int[] MEDAL_SLOTS = { 11, 13, 15 };
    private static final int[] REST_SLOTS = { 19, 20, 21, 23, 24, 25 };

    public void openTop(Player p) {
        TwixQuestPlugin tp = TwixQuestPlugin.inst();
        QuestRegistry reg = tp.getQuestRegistry();
        int total = reg.size();

        // Берём весь список, чтобы показать и своё место тоже.
        List<PlayerQuestData.TopEntry> all = tp.getPlayerData().getTop(1000);

        Inventory inv = Bukkit.createInventory(null, 27, TITLE_TOP);
        frameFill(inv, Material.PURPLE_STAINED_GLASS_PANE,
                0, 1, 2, 3, 5, 6, 7, 8,
                9, 10, 12, 14, 16, 17,
                18, 26);

        // Своё место — в шапке, как в главном меню
        inv.setItem(4, ownPlaceIcon(p, all, total));

        // 1-3 места — медали
        for (int i = 0; i < MEDAL_SLOTS.length; i++) {
            PlayerQuestData.TopEntry e = i < all.size() ? all.get(i) : null;
            inv.setItem(MEDAL_SLOTS[i], topEntryIcon(i + 1, e, total, MEDAL_HEX[i], MEDAL_ITEM[i]));
        }
        // 4-9 места
        for (int i = 0; i < REST_SLOTS.length; i++) {
            int place = 4 + i;
            PlayerQuestData.TopEntry e = place - 1 < all.size() ? all.get(place - 1) : null;
            inv.setItem(REST_SLOTS[i], topEntryIcon(place, e, total, "BB8CFF", Material.NAME_TAG));
        }

        inv.setItem(22, backButton());
        states.put(p.getUniqueId(), new MenuState(MenuKind.TOP, 27, inv));
        p.openInventory(inv);
    }

    /** Иконка одной строки топа. {@code entry == null} — место ещё не занято. */
    private ItemStack topEntryIcon(int place, PlayerQuestData.TopEntry entry, int total, String hex, Material mat) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        String color = "<#" + hex + ">";
        if (entry == null) {
            meta.displayName(TextUtil.mm("<dark_gray><bold>Место #" + place + "</bold> <gray>— пусто"));
            meta.lore(List.of(
                    TextUtil.mm("<dark_gray>─────────"),
                    TextUtil.mm("<dark_gray>Здесь пока никого."),
                    TextUtil.mm(color + "<italic>Стань первым!")
            ));
        } else {
            // Имя игрока экранируем: символы < и > не должны ломать MiniMessage.
            String safeName = TextUtil.escapeMiniMessage(entry.name() == null ? "???" : entry.name());
            meta.displayName(TextUtil.mm(color + "<bold>Место #" + place + "</bold> <white>" + safeName));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.mm("<dark_gray>─────────"));
            lore.add(TextUtil.mm("<gray>Квестов пройдено: " + color + "<bold>" + entry.completed()
                    + "</bold><gray>/" + total));
            lore.add(TextUtil.mm("<gray>Время: " + color + "<bold>" + formatDuration(entry.durationMs())));
            lore.add(TextUtil.mm("<dark_gray>─────────"));
            lore.add(barComponent(entry.completed(), total));
            lore.add(TextUtil.mm(place == 1
                    ? "<#F1C40F><italic>Лидер сервера!"
                    : "<#D3A8FF><italic>Так держать!"));
            meta.lore(lore);
        }
        meta.addItemFlags(ItemFlag.values());
        stack.setItemMeta(meta);
        return stack;
    }

    /** «Ваше место в топе» — шапка меню. */
    private ItemStack ownPlaceIcon(Player p, List<PlayerQuestData.TopEntry> all, int total) {
        int place = -1;
        PlayerQuestData.TopEntry mine = null;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).uuid().equals(p.getUniqueId())) {
                place = i + 1;
                mine = all.get(i);
                break;
            }
        }
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.mm("<#9B59FF><bold>Ваше место в топе"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.mm("<dark_gray>─────────"));
            if (mine == null) {
                lore.add(TextUtil.mm("<gray>Вы ещё не прошли ни одного квеста."));
                lore.add(TextUtil.mm("<#D3A8FF><italic>Начните прямо сейчас!"));
            } else {
                lore.add(TextUtil.mm("<gray>Место: <#F1C40F><bold>#" + place + "</bold> <dark_gray>из <#F1C40F>"
                        + all.size()));
                lore.add(TextUtil.mm("<gray>Квестов пройдено: <#F1C40F>" + mine.completed() + "<gray>/" + total));
                lore.add(TextUtil.mm("<gray>Время: <#F1C40F>" + formatDuration(mine.durationMs())));
            }
            lore.add(TextUtil.mm("<dark_gray>─────────"));
            lore.add(TextUtil.mm("<gray>Топ строится по числу пройденных"));
            lore.add(TextUtil.mm("<gray>квестов, при равенстве — по времени."));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.values());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    // ------------------------------------------------------------------
    // Профиль
    // ------------------------------------------------------------------
    public void openProfile(Player p) {
        TwixQuestPlugin tp = TwixQuestPlugin.inst();
        UUID id = p.getUniqueId();
        QuestRegistry reg = tp.getQuestRegistry();
        int done = tp.getPlayerData().countMainCompleted(id, reg);
        int total = reg.size();

        Inventory inv = Bukkit.createInventory(null, 27, TITLE_PROF);
        frameFill(inv, Material.PURPLE_STAINED_GLASS_PANE, 0, 1, 2, 3, 5, 6, 7, 8,
                18, 19, 20, 21, 23, 24, 25, 26);

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta hm = head.getItemMeta();
        if (hm != null) {
            hm.displayName(TextUtil.mm(
                    "<gradient:#BB8CFF:#9B59FF><bold>" + p.getName() + "</bold></gradient>"));
            long started = tp.getPlayerData().startTime(id);
            long playtime = System.currentTimeMillis() - started;
            int percent = total > 0 ? (int) Math.round(done * 100.0 / total) : 0;
            hm.lore(List.of(
                    TextUtil.mm("<dark_gray>─────────"),
                    TextUtil.mm("<gray>Время прохождения:"),
                    TextUtil.mm("<#D3A8FF>" + formatDuration(playtime)),
                    TextUtil.mm("<dark_gray>─────────"),
                    TextUtil.mm("<gray>Основные квесты: <#F1C40F>" + done + "<gray>/<#F1C40F>" + total +
                            " <dark_gray>(<#BB8CFF>" + percent + "%<dark_gray>)"),
                    barComponent(done, total),
                    TextUtil.mm("<dark_gray>─────────"),
                    TextUtil.mm("<gray>Клановые квесты: <#F1C40F>0<gray>/<#F1C40F>0 <dark_gray>(WIP)")
            ));
            hm.addItemFlags(ItemFlag.values());
            head.setItemMeta(hm);
        }
        inv.setItem(13, head);
        inv.setItem(22, backButton());
        states.put(p.getUniqueId(), new MenuState(MenuKind.PROFILE, 27, inv));
        p.openInventory(inv);
    }

    // ------------------------------------------------------------------
    // Информация о квесте
    // ------------------------------------------------------------------

    private static final int INFO_SIZE = 45;
    private static final int INFO_CARD = 4;
    private static final int INFO_ROW_NEEDS = 9;
    private static final int INFO_ROW_REWARDS = 18;
    private static final int INFO_ACTION = 31;
    private static final int INFO_BACK = 40;

    public void openInfo(Player p, QuestDefinition q) {
        Inventory inv = Bukkit.createInventory(null, INFO_SIZE, TITLE_INFO);
        MenuState st = new MenuState(MenuKind.QUEST_INFO, INFO_SIZE, inv);
        st.questId = q.id;
        fillInfo(p, st, q);
        states.put(p.getUniqueId(), st);
        p.openInventory(inv);
    }

    /**
     * Окно собирается строго из переданного квеста {@code q}: карточка, что нужно,
     * награды (настоящие предметы с чарами), кнопка действия.
     */
    private void fillInfo(Player p, MenuState st, QuestDefinition q) {
        TwixQuestPlugin tp = TwixQuestPlugin.inst();
        UUID id = p.getUniqueId();
        QuestManager qm = tp.getQuestManager();
        Inventory inv = st.inventory;
        inv.clear();
        st.slotQuest.clear();

        boolean completed = tp.getPlayerData().isCompleted(id, q.id);
        QuestDefinition active = qm.currentQuest(id);
        boolean isActive = active != null && active.id == q.id;

        inv.setItem(INFO_CARD, infoCard(q, completed, isActive));

        // Что нужно
        List<ItemStack> needs = needIcons(p, q, qm, completed, isActive);
        int[] needCols = centeredColumns(needs.size());
        for (int i = 0; i < needs.size() && i < needCols.length; i++) {
            int slot = INFO_ROW_NEEDS + needCols[i];
            inv.setItem(slot, needs.get(i));
            if (isActive && q.isClaimable()) st.slotQuest.put(slot, q.id); // иконки тоже кнопки «вложить»
        }

        // Награды
        List<ItemStack> rewards = rewardIcons(q);
        int[] rewardCols = centeredColumns(rewards.size());
        for (int i = 0; i < rewards.size() && i < rewardCols.length; i++) {
            inv.setItem(INFO_ROW_REWARDS + rewardCols[i], rewards.get(i));
        }

        inv.setItem(INFO_ACTION, actionButton(p, q, qm, completed, isActive));
        if (isActive && q.isClaimable()) st.slotQuest.put(INFO_ACTION, q.id);
        inv.setItem(INFO_BACK, backButton());

        for (int s = 0; s < INFO_SIZE; s++) {
            if (inv.getItem(s) == null) frameFill(inv, Material.PURPLE_STAINED_GLASS_PANE, s);
        }
    }

    private ItemStack infoCard(QuestDefinition q, boolean completed, boolean isActive) {
        ItemStack stack = q.icon();
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.mm("<#9B59FF><bold>Квест #" + q.id + " <dark_gray>· <#D3A8FF>" + q.name));
            List<String> lore = new ArrayList<>();
            lore.add(SEP);
            lore.add("<gray>Статус: " + (completed ? "<#27AE60><bold>Выполнен ✔"
                    : isActive ? "<#F1C40F><bold>Текущий" : "<dark_gray>Закрыт"));
            lore.add("<gray>Тип: <#D3A8FF>" + q.shortType());
            lore.add(SEP);
            lore.add("<gray>Условие:");
            lore.addAll(wrap(q.description, "<#D3A8FF>"));
            lore.add(SEP);
            meta.lore(components(lore));
            meta.addItemFlags(ItemFlag.values());
            if (isActive) meta.setEnchantmentGlintOverride(true);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** Иконки «что нужно»: у вложений — сами предметы с прогрессом, у остальных — одна иконка цели. */
    private List<ItemStack> needIcons(Player p, QuestDefinition q, QuestManager qm, boolean completed, boolean isActive) {
        UUID id = p.getUniqueId();
        List<ItemStack> out = new ArrayList<>();
        if (q.isClaimable()) {
            for (QuestDefinition.MaterialRequirement r : q.requirements()) {
                ItemStack stack = new ItemStack(r.material, Math.max(1, Math.min(r.amount, r.material.getMaxStackSize())));
                ItemMeta meta = stack.getItemMeta();
                if (meta == null) { out.add(stack); continue; }
                List<String> lore = new ArrayList<>();
                lore.add(SEP);
                if (q.consume) {
                    int dep = completed ? r.amount : Math.min(r.amount, qm.deposited(id, q, r.material));
                    lore.add("<gray>Вложено: " + (dep >= r.amount ? "<#27AE60>" : "<#F1C40F>") + dep
                            + "<dark_gray>/<#F1C40F>" + r.amount);
                    lore.add(TextUtil.bar(dep, r.amount, 12));
                    if (isActive && dep < r.amount) {
                        int bag = qm.countInInventory(p, r.material, true);
                        lore.add("<gray>В сумке: " + (bag >= r.amount - dep ? "<#27AE60>" : "<#E74C3C>") + bag);
                    }
                } else {
                    lore.add("<gray>Нужно скрафтить: <#F1C40F>" + r.amount);
                    if (isActive) {
                        int bag = qm.countInInventory(p, r.material, false);
                        lore.add("<gray>В сумке: " + (bag >= r.amount ? "<#27AE60>" : "<#E74C3C>") + bag);
                    }
                }
                if (completed) lore.add("<#27AE60>✔ Выполнено");
                else if (isActive && q.consume) {
                    lore.add(SEP);
                    lore.add("<#27AE60><bold>ЛКМ <dark_gray>— <gray>вложить");
                }
                meta.lore(components(lore));
                meta.addItemFlags(ItemFlag.values());
                stack.setItemMeta(meta);
                out.add(stack);
            }
            return out;
        }

        // Квест-действие: одна иконка цели со счётчиком
        ItemStack goal = q.icon();
        ItemMeta meta = goal.getItemMeta();
        if (meta != null) {
            int done = completed ? q.amount : qm.currentProgress(id, q);
            meta.displayName(TextUtil.mm("<#D3A8FF><bold>Цель"));
            List<String> lore = new ArrayList<>();
            lore.add(SEP);
            lore.add("<gray>" + q.shortType() + ": <#F1C40F>" + done + "<dark_gray>/<#F1C40F>" + q.amount);
            lore.add(TextUtil.bar(done, q.amount, 12));
            if (completed) lore.add("<#27AE60>✔ Выполнено");
            meta.lore(components(lore));
            meta.addItemFlags(ItemFlag.values());
            goal.setItemMeta(meta);
        }
        out.add(goal);
        return out;
    }

    /** Награды настоящими предметами (имя, чары — ровно как у выдаваемых) плюс значки валют. */
    private List<ItemStack> rewardIcons(QuestDefinition q) {
        List<ItemStack> out = new ArrayList<>();
        for (RewardItem item : q.reward.items) out.add(item.preview());
        if (q.reward.vaultCoins > 0) {
            out.add(currencyIcon(Material.GOLD_INGOT, "<#F1C40F><bold>" + q.reward.coinsText(),
                    "<gray>Зачислятся на ваш счёт."));
        }
        if (q.reward.twixCoins > 0) {
            out.add(currencyIcon(Material.AMETHYST_SHARD, "<#7FDBFF><bold>" + q.reward.twixText(),
                    "<gray>Донатная валюта сервера."));
        }
        if (q.reward.expLevels > 0) {
            out.add(currencyIcon(Material.EXPERIENCE_BOTTLE, "<#7CFC00><bold>" + q.reward.expText(),
                    "<gray>Начислятся сразу."));
        }
        if (out.isEmpty()) {
            out.add(currencyIcon(Material.BARRIER, "<dark_gray><bold>Без награды",
                    "<gray>Этот квест — просто шаг вперёд."));
        }
        return out;
    }

    private ItemStack currencyIcon(Material mat, String name, String line) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.mm(name));
            meta.lore(components(List.of(SEP, line, SEP, "<#F1C40F>▸ Награда за квест")));
            meta.addItemFlags(ItemFlag.values());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack actionButton(Player p, QuestDefinition q, QuestManager qm, boolean completed, boolean isActive) {
        UUID id = p.getUniqueId();
        Material mat;
        String name;
        List<String> lore = new ArrayList<>();
        lore.add(SEP);
        boolean glow = false;
        if (completed) {
            mat = Material.EMERALD;
            name = "<#27AE60><bold>Квест выполнен";
            long at = TwixQuestPlugin.inst().getPlayerData().completedAt(id, q.id);
            if (at > 0) {
                lore.add("<gray>Дата: <#D3A8FF>" + new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(new java.util.Date(at)));
            }
        } else if (isActive && q.isClaimable() && q.consume) {
            mat = Material.HOPPER;
            name = "<#27AE60><bold>Вложить предметы";
            glow = true;
            lore.add("<gray>Заберём из инвентаря столько,");
            lore.add("<gray>сколько нужно (можно по частям).");
            lore.add(SEP);
            lore.addAll(qm.remainingLines(id, q));
            lore.add(SEP);
            lore.add("<#27AE60><bold>ЛКМ <dark_gray>— <gray>вложить");
        } else if (isActive && q.isClaimable()) {
            mat = Material.CRAFTING_TABLE;
            name = "<#27AE60><bold>Засчитать предмет";
            glow = true;
            lore.add("<gray>Квест засчитывается сам, когда вы");
            lore.add("<gray>скрафтите предмет. Если он уже у вас —");
            lore.add("<gray>нажмите, предмет останется при вас.");
            lore.add(SEP);
            lore.add("<#27AE60><bold>ЛКМ <dark_gray>— <gray>засчитать");
        } else if (isActive) {
            mat = Material.CLOCK;
            name = "<#F1C40F><bold>Выполняется автоматически";
            lore.add("<gray>Просто сделайте то, что написано");
            lore.add("<gray>в условии, — квест засчитается сам.");
        } else {
            mat = Material.BARRIER;
            name = "<dark_gray><bold>Недоступно";
        }
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.mm(name));
            meta.lore(components(lore));
            meta.addItemFlags(ItemFlag.values());
            if (glow) meta.setEnchantmentGlintOverride(true);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** Колонки для {@code count} иконок в ряду из 9 слотов — по центру. */
    static int[] centeredColumns(int count) {
        return switch (Math.min(count, 9)) {
            case 0 -> new int[0];
            case 1 -> new int[]{4};
            case 2 -> new int[]{3, 5};
            case 3 -> new int[]{2, 4, 6};
            case 4 -> new int[]{1, 3, 5, 7};
            default -> {
                int c = Math.min(count, 9);
                int start = (9 - c) / 2;
                int[] cols = new int[c];
                for (int i = 0; i < c; i++) cols[i] = start + i;
                yield cols;
            }
        };
    }

    // ------------------------------------------------------------------
    // Обработка кликов
    // ------------------------------------------------------------------
    public boolean handleClick(InventoryClickEvent e) {
        // Сначала проверяем, что это наше меню — иначе не вмешиваемся.
        if (!(e.getWhoClicked() instanceof Player p)) return false;
        MenuState st = states.get(p.getUniqueId());
        if (st == null) return false;
        if (!isOurMenu(p, e.getInventory())) return false;

        // Теперь точно наше меню — блокируем любые перемещения и кражи.
        e.setCancelled(true);
        e.setResult(org.bukkit.event.Event.Result.DENY);

        int slot = e.getRawSlot();
        if (slot < 0 || slot >= st.size) return true; // клики по своему инвентарю просто блокируем

        switch (st.kind) {
            case MAIN -> {
                if (slot == 11) defer(p, () -> openTree(p));
                else if (slot == 13) defer(p, () -> openTop(p));
                else if (slot == 15) defer(p, () -> openClans(p));
                else if (slot == 22) defer(p, () -> openProfile(p));
                else if (slot == 4) {
                    // Звёздочка сверху — просто информация.
                    p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
                }
            }
            case TREE -> handleTreeClick(p, st, e, slot);
            case QUEST_INFO -> handleInfoClick(p, st, slot);
            case CLANS, TOP, PROFILE -> {
                if (slot == 22) defer(p, () -> openMain(p));
            }
        }
        return true;
    }

    private void handleTreeClick(Player p, MenuState st, InventoryClickEvent e, int slot) {
        if (slot == st.size - 5) { defer(p, () -> openMain(p)); return; }
        if (slot == st.size - 6 && st.page > 0) {
            int target = st.page - 1;
            defer(p, () -> openTree(p, target));
            return;
        }
        if (slot == st.size - 4 && st.page < st.pages - 1) {
            int target = st.page + 1;
            defer(p, () -> openTree(p, target));
            return;
        }

        Integer questId = st.slotQuest.get(slot);
        if (questId == null) return;
        TwixQuestPlugin tp = TwixQuestPlugin.inst();
        QuestDefinition q = tp.getQuestRegistry().byId(questId);
        if (q == null) return;

        UUID id = p.getUniqueId();
        boolean completed = tp.getPlayerData().isCompleted(id, q.id);
        QuestDefinition active = tp.getQuestManager().currentQuest(id);
        boolean isActive = active != null && active.id == q.id;

        if (!completed && !isActive) {
            // Заблокированный квест — тактильный отклик
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_IRON_DOOR_CLOSE, 0.5f, 1.6f);
            return;
        }
        if (isActive && q.isClaimable() && !e.getClick().isRightClick()) {
            doDeposit(p, st, q); // ЛКМ — вложить предметы
            return;
        }
        p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
        defer(p, () -> openInfo(p, q)); // ПКМ (и ЛКМ у квестов-действий) — информация
    }

    private void handleInfoClick(Player p, MenuState st, int slot) {
        TwixQuestPlugin tp = TwixQuestPlugin.inst();
        QuestDefinition q = tp.getQuestRegistry().byId(st.questId);
        if (slot == INFO_BACK) {
            int page = q == null ? 0 : Math.max(0, tp.getQuestRegistry().indexOf(q.id)) / TREE_PAGE_SIZE;
            defer(p, () -> openTree(p, page));
            return;
        }
        if (q != null && st.slotQuest.containsKey(slot)) {
            doDeposit(p, st, q);
        }
    }

    /** Клик «вложить»: забираем предметы и перерисовываем окно на месте. */
    private void doDeposit(Player p, MenuState st, QuestDefinition q) {
        QuestManager.DepositResult result = TwixQuestPlugin.inst().getQuestManager().deposit(p, q);
        if (st.kind == MenuKind.QUEST_INFO && result.completed()) {
            // Квест закрыт — возвращаемся к дереву, где уже светится следующий.
            defer(p, () -> openTree(p));
            return;
        }
        if (st.kind == MenuKind.TREE) fillTree(p, st);
        else if (st.kind == MenuKind.QUEST_INFO) fillInfo(p, st, q);
    }

    /** Запланировать открытие инвентаря на следующий тик — иначе Bukkit теряет события. */
    private void defer(Player p, Runnable action) {
        Bukkit.getScheduler().runTask(plugin, action);
    }

    /** Не даём перетаскивать предметы в наше меню (они бы просто пропали при закрытии). */
    public void handleDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!isOurMenu(p, e.getInventory())) return;
        int size = e.getInventory().getSize();
        for (int raw : e.getRawSlots()) {
            if (raw < size) {
                e.setCancelled(true);
                return;
            }
        }
    }

    public void handleClose(InventoryCloseEvent e) {
        // Удаляем state, только если закрытие — нашего инвентаря (по identity).
        // При defer-переходе между нашими меню close срабатывает на СТАРОМ инвентаре,
        // но новый state уже указывает на НОВЫЙ inventory — здесь == не сойдётся,
        // state не удалится.
        Player p = (Player) e.getPlayer();
        MenuState st = states.get(p.getUniqueId());
        if (st != null && e.getInventory() == st.inventory) {
            states.remove(p.getUniqueId());
        }
    }

    // ------------------------------------------------------------------
    // Утилиты оформления
    // ------------------------------------------------------------------

    /** Заполнение кнопки-разделителя в меню — фиолетовое стекло. */
    private void frameFill(Inventory inv, Material mat, int... slots) {
        ItemStack pane = new ItemStack(mat);
        ItemMeta pm = pane.getItemMeta();
        if (pm != null) { pm.displayName(Component.empty()); pane.setItemMeta(pm); }
        for (int s : slots) if (inv.getSize() > s && inv.getItem(s) == null) inv.setItem(s, pane);
    }

    /** Кнопка "Назад" — железная стрелка с надёжной блокировкой. */
    private ItemStack backButton() {
        ItemStack stack = new ItemStack(Material.ARROW);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.mm("<#9B59FF><bold>← Назад"));
            meta.lore(List.of(
                    TextUtil.mm("<dark_gray>─────────"),
                    TextUtil.mm("<#D3A8FF><italic>Вернуться в предыдущее меню")
            ));
            meta.addItemFlags(ItemFlag.values());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** Большая кнопка — декоративный айтем с подсказкой. */
    private ItemStack bigButton(Material mat, String name, List<String> lore) {
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
        return stack;
    }

    /** Простая кнопка без декоративной рамки. */
    private ItemStack button(Material mat, String name, List<String> lore, boolean glow) {
        ItemStack stack = bigButton(mat, name, lore);
        if (glow) {
            ItemMeta meta = stack.getItemMeta();
            if (meta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta esm) {
                esm.addStoredEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                stack.setItemMeta(esm);
            } else if (meta != null) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                stack.setItemMeta(meta);
            }
        }
        return stack;
    }

    /** Прогресс-бар: ▰▰▰▰▱▱▱▱▱▱ (10 сегментов). */
    private Component barComponent(int done, int total) {
        if (total <= 0) return TextUtil.mm("<dark_gray>▱▱▱▱▱▱▱▱▱▱");
        int segments = 10;
        int filled = (int) Math.round(done * (double) segments / total);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments; i++) {
            if (i < filled) sb.append("<#9B59FF>▰");
            else sb.append("<dark_gray>▱");
        }
        return TextUtil.mm(sb.toString());
    }

    /** MiniMessage-строки лора → компоненты. */
    private static List<Component> components(List<String> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String l : lines) out.add(TextUtil.mm(l));
        return out;
    }

    /** Переносит длинный текст по словам, чтобы подсказка не растягивалась на пол-экрана. */
    private static List<String> wrap(String text, String color) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > WRAP) {
                out.add(color + line);
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) out.add(color + line);
        return out;
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
        final Inventory inventory;
        /** Слот → id квеста: по нему определяем, на что кликнули (дерево и окно информации). */
        final Map<Integer, Integer> slotQuest = new HashMap<>();
        /** Страница дерева квестов и общее число страниц. */
        int page;
        int pages = 1;
        /** Квест, который показывает окно информации. */
        int questId;
        MenuState(MenuKind k, int size, Inventory inv) { this.kind = k; this.size = size; this.inventory = inv; }
    }
}
