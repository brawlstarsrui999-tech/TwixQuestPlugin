package twix.quest.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
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

    public enum MenuKind { MAIN, TREE, CLANS, TOP, PROFILE, REWARD_INFO }

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
            TextUtil.mm("<gradient:#BB8CFF:#9B59FF><bold>TwixQuest</bold></gradient> <dark_gray>· <#D3A8FF>Топ скорости");
    private static final net.kyori.adventure.text.Component TITLE_PROF =
            TextUtil.mm("<gradient:#BB8CFF:#9B59FF><bold>TwixQuest</bold></gradient> <dark_gray>· <#D3A8FF>Профиль");
    private static final net.kyori.adventure.text.Component TITLE_RW =
            TextUtil.mm("<gradient:#BB8CFF:#9B59FF><bold>TwixQuest</bold></gradient> <dark_gray>· <#D3A8FF>Награда");

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
                "<#9B59FF><bold>Топ скорости</bold>",
                List.of(
                        "<dark_gray>─────────",
                        "<gray>Тройка самых быстрых",
                        "<gray>путешественников.",
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
    public void openTree(Player p) {
        TwixQuestPlugin tp = TwixQuestPlugin.inst();
        PlayerQuestData data = tp.getPlayerData();
        QuestRegistry reg = tp.getQuestRegistry();
        List<QuestDefinition> all = reg.all();

        // Берём размер: 27 слотов на каждые 14 видимых квестов (выполненные + активный).
        int slots = Math.min(54, Math.max(27, ((all.size() + 8) / 9 + 1) * 9));
        Inventory inv = Bukkit.createInventory(null, slots, TITLE_TREE);
        UUID uuid = p.getUniqueId();

        // Соберём индексы, куда ставим видимые квесты (выполненные + активный).
        // Будущие заменяются на «закрытый» железный блок.
        QuestDefinition active = tp.getQuestManager().currentQuest(uuid);

        // Заполняем внутренние слоты (не граница и не нижняя панель) последовательно.
        List<Integer> availableSlots = new ArrayList<>();
        for (int i = 0; i < slots; i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || row == slots / 9 - 1) continue;
            if (col == 0 || col == 8) continue;
            availableSlots.add(i);
        }

        for (int i = 0; i < Math.min(all.size(), availableSlots.size()); i++) {
            QuestDefinition q = all.get(i);
            int slot = availableSlots.get(i);
            boolean completed = data.isCompleted(uuid, q.id);
            boolean isActive = active != null && active.id == q.id;
            boolean future = !completed && !isActive;

            ItemStack stack;
            if (future) {
                stack = lockedQuestIcon(q.id);
            } else if (completed) {
                stack = completedQuestIcon(q);
            } else {
                stack = activeQuestIcon(q, tp.getQuestManager().currentProgress(uuid, q));
            }
            inv.setItem(slot, stack);
        }

        // Рамка: верхняя и нижняя строки полностью
        int[] top = new int[9];
        int[] bottom = new int[9];
        for (int i = 0; i < 9; i++) { top[i] = i; bottom[i] = slots - 9 + i; }
        frameFill(inv, Material.PURPLE_STAINED_GLASS_PANE, top);
        frameFill(inv, Material.PURPLE_STAINED_GLASS_PANE, bottom);

        // Кнопка "Назад"
        inv.setItem(slots - 5, backButton());

        states.put(p.getUniqueId(), new MenuState(MenuKind.TREE, slots, inv));
        p.openInventory(inv);
    }

    private ItemStack lockedQuestIcon(int id) {
        // Будущий квест — железный блок с замком, загадочное оформление
        ItemStack stack = new ItemStack(Material.IRON_BLOCK);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.mm("<dark_gray><bold>???"));
            meta.lore(List.of(
                    TextUtil.mm("<dark_gray>─────────"),
                    TextUtil.mm("<dark_gray>Квест <gray>#" + id),
                    TextUtil.mm("<dark_gray>─────────"),
                    TextUtil.mm("<dark_gray><italic>Завершите предыдущие"),
                    TextUtil.mm("<dark_gray><italic>квесты, чтобы открыть.")
            ));
            meta.addItemFlags(ItemFlag.values());
            meta.setHideTooltip(false);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack completedQuestIcon(QuestDefinition q) {
        ItemStack stack = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.mm(
                    "<#27AE60><bold>✓ <#BB8CFF>" + q.name));
            meta.lore(List.of(
                    TextUtil.mm("<dark_gray>─────────"),
                    TextUtil.mm("<gray>#" + q.id + " <dark_gray>· <#27AE60><bold>Завершено"),
                    TextUtil.mm("<dark_gray>─────────"),
                    TextUtil.mm("<#D3A8FF>" + q.description),
                    TextUtil.mm("<dark_gray>─────────"),
                    TextUtil.mm("<dark_gray>Награда:"),
                    TextUtil.mm(q.reward.describe())
            ));
            meta.addItemFlags(ItemFlag.values());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack activeQuestIcon(QuestDefinition q, int progress) {
        ItemStack stack = new ItemStack(q.icon().getType());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.mm(
                    "<#D3A8FF><bold>● <#9B59FF>Квест #" + q.id + " <dark_gray>· <#D3A8FF>" + q.name));
            meta.lore(List.of(
                    TextUtil.mm("<dark_gray>─────────"),
                    TextUtil.mm("<gray>" + q.shortType()),
                    TextUtil.mm("<#D3A8FF>" + q.description),
                    TextUtil.mm("<dark_gray>─────────"),
                    TextUtil.mm("<gray>Прогресс: <#F1C40F>" + progress + "<gray>/<#F1C40F>" + q.amount),
                    TextUtil.mm("<dark_gray>─────────"),
                    TextUtil.mm("<gray>Награда:"),
                    TextUtil.mm(q.reward.describe()),
                    TextUtil.mm("<dark_gray>─────────"),
                    TextUtil.mm("<#D3A8FF><bold>ЛКМ <dark_gray>— <gray>подробности")
            ));
            meta.addItemFlags(ItemFlag.values());
            stack.setItemMeta(meta);
        }
        return stack;
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
    // Топ скорости
    // ------------------------------------------------------------------
    public void openTop(Player p) {
        TwixQuestPlugin tp = TwixQuestPlugin.inst();
        List<PlayerQuestData.SpeedEntry> top = tp.getPlayerData().getSpeedTop(tp.getQuestRegistry(), 3);
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_TOP);
        frameFill(inv, Material.PURPLE_STAINED_GLASS_PANE, 0, 1, 2, 3, 5, 6, 7, 8,
                18, 19, 20, 21, 23, 24, 25, 26);

        Material[] medals = { Material.GOLD_INGOT, Material.IRON_INGOT, Material.COPPER_INGOT };
        String[] medalColor = { "#F1C40F", "#BDC3C7", "#CD7F32" };
        for (int i = 0; i < 3; i++) {
            ItemStack stack = new ItemStack(medals[i]);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                if (i < top.size()) {
                    PlayerQuestData.SpeedEntry e = top.get(i);
                    // Экранируем имя игрока, чтобы символы < и > не сломали MiniMessage-парсер.
                    String safeName = TextUtil.escapeMiniMessage(
                            e.name() == null ? "???" : e.name());
                    meta.displayName(TextUtil.mm(
                            "<#" + medalColor[i] + "><bold>Медаль #" + (i + 1) + "</bold></#" + medalColor[i] + ">"
                                    + " <#D3A8FF>" + safeName));
                    meta.lore(List.of(
                            TextUtil.mm("<dark_gray>─────────"),
                            TextUtil.mm("<gray>Прошёл все квесты за:"),
                            TextUtil.mm("<#" + medalColor[i] + "><bold>" + formatDuration(e.durationMs())),
                            TextUtil.mm("<dark_gray>─────────"),
                            TextUtil.mm("<#D3A8FF><italic>Поздравляем победителя!")
                    ));
                } else {
                    meta.displayName(TextUtil.mm(
                            "<#" + medalColor[i] + "><bold>Медаль #" + (i + 1) + "</bold></#" + medalColor[i] + ">"
                                    + " <dark_gray>— пусто"));
                    meta.lore(List.of(
                            TextUtil.mm("<dark_gray>─────────"),
                            TextUtil.mm("<dark_gray>Здесь пока никого."),
                            TextUtil.mm("<#D3A8FF><italic>Будь первым!")
                    ));
                }
                meta.addItemFlags(ItemFlag.values());
                stack.setItemMeta(meta);
            }
            inv.setItem(11 + i * 2, stack);
        }
        inv.setItem(22, backButton());
        states.put(p.getUniqueId(), new MenuState(MenuKind.TOP, 27, inv));
        p.openInventory(inv);
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
    // Детали квеста / награды
    // ------------------------------------------------------------------
    private void showRewardInfo(Player p, QuestDefinition q) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_RW);
        frameFill(inv, Material.PURPLE_STAINED_GLASS_PANE, 0, 1, 2, 3, 5, 6, 7, 8,
                18, 19, 20, 21, 23, 24, 25, 26);

        ItemStack icon = q.icon();
        ItemMeta m = icon.getItemMeta();
        if (m != null) {
            m.displayName(TextUtil.mm("<#9B59FF><bold>" + q.name));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.mm("<dark_gray>─────────"));
            lore.add(TextUtil.mm("<gray>Тип: <#D3A8FF>" + q.shortType()));
            lore.add(TextUtil.mm("<dark_gray>─────────"));
            lore.add(TextUtil.mm("<gray>Условие:"));
            lore.add(TextUtil.mm("<#D3A8FF>" + q.description));
            lore.add(TextUtil.mm("<dark_gray>─────────"));
            lore.add(TextUtil.mm("<#F1C40F><bold>Награда:"));
            lore.add(TextUtil.mm(q.reward.describe()));
            lore.add(TextUtil.mm("<dark_gray>─────────"));
            m.lore(lore);
            m.addItemFlags(ItemFlag.values());
            icon.setItemMeta(m);
        }
        inv.setItem(13, icon);
        inv.setItem(22, backButton());
        states.put(p.getUniqueId(), new MenuState(MenuKind.REWARD_INFO, 27, inv));
        p.openInventory(inv);
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
        if (slot < 0) return true;

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
            case TREE -> {
                Inventory top = e.getInventory();
                int size = top.getSize();
                // Кнопка "Назад" всегда в нижней средней строке (вторая справа).
                if (slot == size - 5) { defer(p, () -> openMain(p)); return true; }
                // Клик по квесту
                ItemStack clicked = top.getItem(slot);
                if (clicked != null && clicked.getType() != Material.PURPLE_STAINED_GLASS_PANE
                        && clicked.getType() != Material.IRON_BLOCK) {
                    // Найти квест по имени (для активного или выполненного)
                    TwixQuestPlugin tp = TwixQuestPlugin.inst();
                    ItemMeta cm = clicked.getItemMeta();
                    if (cm != null && cm.displayName() != null) {
                        String plain = MiniMessage.miniMessage().serialize(cm.displayName());
                        for (QuestDefinition q : tp.getQuestRegistry().all()) {
                            if (plain.contains("#" + q.id + " ") || plain.contains("#" + q.id + " <")
                                    || plain.contains("Квест #" + q.id)) {
                                if (ClickType.LEFT.equals(e.getClick()) || ClickType.RIGHT.equals(e.getClick())) {
                                    p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
                                    defer(p, () -> showRewardInfo(p, q));
                                }
                                break;
                            }
                        }
                    }
                } else if (clicked != null && clicked.getType() == Material.IRON_BLOCK) {
                    // Заблокированный квест — тактильный отклик
                    p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_IRON_DOOR_CLOSE, 0.5f, 1.6f);
                }
            }
            case CLANS, TOP, PROFILE -> {
                if (slot == 22) defer(p, () -> openMain(p));
            }
            case REWARD_INFO -> {
                if (slot == 22) defer(p, () -> openTree(p));
            }
        }
        return true;
    }

    /** Запланировать открытие инвентаря на следующий тик — иначе Bukkit теряет события. */
    private void defer(Player p, Runnable action) {
        Bukkit.getScheduler().runTask(plugin, action);
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

    private int[] borderTop(int size) {
        int[] r = new int[9];
        for (int i = 0; i < 9; i++) r[i] = i;
        return r;
    }

    private int[] borderBottom(int size) {
        int[] r = new int[9];
        for (int i = 0; i < 9; i++) r[i] = size - 9 + i;
        return r;
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
        MenuState(MenuKind k, int size, Inventory inv) { this.kind = k; this.size = size; this.inventory = inv; }
    }
}
