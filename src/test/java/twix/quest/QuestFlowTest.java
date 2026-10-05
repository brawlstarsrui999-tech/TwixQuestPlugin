package twix.quest;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import twix.quest.manager.QuestManager;
import twix.quest.quest.QuestDefinition;
import twix.quest.reward.RewardItem;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Сквозные сценарии: настоящий плагин поверх эмулированного сервера (MockBukkit),
 * игрок кликает по меню, у него забираются предметы, приходят награды.
 */
class QuestFlowTest {

    private ServerMock server;
    private TwixQuestPlugin plugin;
    private PlayerMock player;
    private UUID id;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(TwixQuestPlugin.class);
        player = server.addPlayer("Steve");
        id = player.getUniqueId();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // ---------------------------------------------------------------- помощники

    private QuestManager qm() { return plugin.getQuestManager(); }

    private QuestDefinition quest(int questId) {
        QuestDefinition q = plugin.getQuestRegistry().byId(questId);
        assertNotNull(q, "нет квеста #" + questId);
        return q;
    }

    /** Закрывает квесты 1..(n-1) как будто игрок их прошёл (с наградами) и очищает инвентарь. */
    private void completeBefore(int questId) {
        for (QuestDefinition q : plugin.getQuestRegistry().all()) {
            if (q.id >= questId) break;
            qm().completeQuest(id, q);
        }
        player.getInventory().clear();
    }

    private int count(Material m) {
        int n = 0;
        for (ItemStack s : player.getInventory().getContents()) {
            if (s != null && s.getType() == m) n += s.getAmount();
        }
        return n;
    }

    private ItemStack find(Material m) {
        for (ItemStack s : player.getInventory().getContents()) {
            if (s != null && s.getType() == m) return s;
        }
        return null;
    }

    private static String plain(Component c) {
        return c == null ? "" : PlainTextComponentSerializer.plainText().serialize(c);
    }

    private static String nameOf(ItemStack s) {
        ItemMeta m = s.getItemMeta();
        return m == null ? "" : plain(m.displayName());
    }

    /** Слот квеста в дереве (все 35 квестов на одной странице, начиная со слота 9). */
    private static int slotOf(int questId) { return 9 + (questId - 1); }

    private InventoryClickEvent click(ClickType type, int slot) {
        return player.simulateInventoryClick(player.getOpenInventory(), type, slot);
    }

    private void tick() { server.getScheduler().performOneTick(); }

    private Inventory top() { return player.getOpenInventory().getTopInventory(); }

    // ---------------------------------------------------------------- награды

    @Test
    void loadsAllThirtyFiveQuests() {
        assertEquals(35, plugin.getQuestRegistry().size());
        assertEquals(1, qm().currentQuest(id).id);
    }

    @Test
    void pluginYmlIsPackagedAndVersionIsFiltered() {
        // Maven подставляет версию только в plugin.yml; quests.yml копируется как есть.
        assertEquals("1.0.0", plugin.getPluginMeta().getVersion());
        assertNotNull(plugin.getCommand("quests"), "команда /quests должна быть описана в plugin.yml");
        assertNotNull(plugin.getCommand("tqadmin"));
    }

    @Test
    void rewardItemsCarryRealEnchantments() {
        ItemStack axe = quest(1).reward.items.get(0).buildStacks().get(0);
        assertEquals(Material.WOODEN_AXE, axe.getType());
        assertEquals(2, axe.getEnchantmentLevel(Enchantment.EFFICIENCY));
        assertEquals("Топор Дровосека", nameOf(axe));

        ItemStack pick4 = quest(4).reward.items.get(0).buildStacks().get(0);
        assertEquals(1, pick4.getEnchantmentLevel(Enchantment.FORTUNE));
        assertEquals(2, pick4.getEnchantmentLevel(Enchantment.EFFICIENCY));

        ItemStack sword7 = quest(7).reward.items.get(0).buildStacks().get(0);
        assertEquals(2, sword7.getEnchantmentLevel(Enchantment.SHARPNESS));

        ItemStack chest = quest(11).reward.items.get(0).buildStacks().get(0);
        assertEquals(Material.DIAMOND_CHESTPLATE, chest.getType());
        assertEquals(4, chest.getEnchantmentLevel(Enchantment.PROTECTION));

        ItemStack pick22 = quest(22).reward.items.get(0).buildStacks().get(0);
        assertEquals(3, pick22.getEnchantmentLevel(Enchantment.FORTUNE));
        assertEquals(5, pick22.getEnchantmentLevel(Enchantment.EFFICIENCY));
        assertEquals(3, pick22.getEnchantmentLevel(Enchantment.UNBREAKING));
        assertEquals(1, pick22.getEnchantmentLevel(Enchantment.MENDING));

        ItemStack sword25 = quest(25).reward.items.get(0).buildStacks().get(0);
        assertEquals(4, sword25.getEnchantmentLevel(Enchantment.SHARPNESS));
        assertEquals(1, sword25.getEnchantmentLevel(Enchantment.FIRE_ASPECT));
        assertEquals(2, sword25.getEnchantmentLevel(Enchantment.UNBREAKING));
    }

    @Test
    void mendingRewardIsTwoSeparateEnchantedBooks() {
        List<ItemStack> books = quest(15).reward.items.get(0).buildStacks();
        assertEquals(2, books.size(), "«2 починки» — это две отдельные книги");
        for (ItemStack book : books) {
            assertEquals(Material.ENCHANTED_BOOK, book.getType());
            assertEquals(1, book.getAmount());
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
            assertEquals(1, meta.getStoredEnchantLevel(Enchantment.MENDING), "чара «Починка» в книге нет");
        }
    }

    @Test
    void maceQuestGivesMaceAndWindBurstBook() {
        QuestDefinition q = quest(20);
        assertEquals(Material.HEAVY_CORE, q.itemMaterial, "навершие булавы — тяжёлое ядро");
        List<RewardItem> items = q.reward.items;
        assertEquals(2, items.size());
        assertEquals(Material.MACE, items.get(0).buildStacks().get(0).getType());
        ItemStack book = items.get(1).buildStacks().get(0);
        assertEquals(Material.ENCHANTED_BOOK, book.getType());
        assertEquals(3, ((EnchantmentStorageMeta) book.getItemMeta()).getStoredEnchantLevel(Enchantment.WIND_BURST));
    }

    // ---------------------------------------------------------------- вложение кликом

    @Test
    void clickingQuestTakesExactlyWhatIsNeededAndPaysReward() {
        player.getInventory().addItem(new ItemStack(Material.OAK_LOG, 15));
        plugin.getMenuManager().openTree(player);

        InventoryClickEvent e = click(ClickType.LEFT, slotOf(1));

        assertTrue(e.isCancelled(), "клик по меню должен быть отменён — предметы нельзя вытащить");
        assertEquals(5, count(Material.OAK_LOG), "из 15 бревен должны забраться ровно 10");
        assertEquals(2, qm().currentQuest(id).id, "квест #1 должен закрыться");
        ItemStack axe = find(Material.WOODEN_AXE);
        assertNotNull(axe, "награда не выдана");
        assertEquals(2, axe.getEnchantmentLevel(Enchantment.EFFICIENCY), "топор должен быть с эффективностью 2");
    }

    @Test
    void rightClickOpensInfoInsteadOfDepositing() {
        player.getInventory().addItem(new ItemStack(Material.OAK_LOG, 10));
        plugin.getMenuManager().openTree(player);

        click(ClickType.RIGHT, slotOf(1));
        tick();

        assertEquals(10, count(Material.OAK_LOG), "ПКМ — это «информация», вкладывать он не должен");
        assertTrue(plain(player.getOpenInventory().title()).contains("Информация о квесте"));
    }

    @Test
    void accidentalClickTypesNeverDeposit() {
        player.getInventory().addItem(new ItemStack(Material.OAK_LOG, 10));
        plugin.getMenuManager().openTree(player);

        click(ClickType.DROP, slotOf(1));
        click(ClickType.NUMBER_KEY, slotOf(1));
        click(ClickType.DOUBLE_CLICK, slotOf(1));

        assertEquals(10, count(Material.OAK_LOG), "клавиша выброса, цифры и двойной клик не должны вкладывать");
    }

    @Test
    void clickingWithoutItemsChangesNothing() {
        plugin.getMenuManager().openTree(player);
        click(ClickType.LEFT, slotOf(1));
        assertEquals(1, qm().currentQuest(id).id);
        assertEquals(0, qm().currentProgress(id, quest(1)));
    }

    @Test
    void depositCanBeDoneInPartsAndProgressIsSavedToDisk() {
        completeBefore(22);
        player.getInventory().addItem(new ItemStack(Material.COBBLESTONE, 400));
        plugin.getMenuManager().openTree(player);

        click(ClickType.LEFT, slotOf(22));
        assertEquals(0, count(Material.COBBLESTONE), "все 400 должны быть вложены");
        assertEquals(400, qm().deposited(id, quest(22), Material.COBBLESTONE));
        assertEquals(22, qm().currentQuest(id).id, "квест ещё не закрыт");

        // прогресс лежит в файле — переживёт выход и рестарт
        plugin.getPlayerData().flushDirty();
        File file = new File(plugin.getDataFolder(), "playerdata/" + id + ".yml");
        assertTrue(file.exists());
        assertEquals(400, YamlConfiguration.loadConfiguration(file).getInt("progress.22.COBBLESTONE"));

        // досдача: нужно ещё 350 — лишнее остаётся у игрока
        player.getInventory().addItem(new ItemStack(Material.COBBLESTONE, 500));
        click(ClickType.LEFT, slotOf(22));
        assertEquals(150, count(Material.COBBLESTONE));
        assertTrue(plugin.getPlayerData().isCompleted(id, 22));
        ItemStack pick = find(Material.DIAMOND_PICKAXE);
        assertNotNull(pick);
        assertEquals(3, pick.getEnchantmentLevel(Enchantment.FORTUNE));
        assertEquals(5, pick.getEnchantmentLevel(Enchantment.EFFICIENCY));
        assertEquals(1, pick.getEnchantmentLevel(Enchantment.MENDING));
    }

    @Test
    void specialItemsWithCustomNameAreNeverConsumed() {
        ItemStack special = new ItemStack(Material.OAK_LOG, 10);
        ItemMeta meta = special.getItemMeta();
        meta.displayName(Component.text("Особое бревно"));
        special.setItemMeta(meta);
        player.getInventory().addItem(special);
        player.getInventory().addItem(new ItemStack(Material.OAK_LOG, 4));

        // цифры «в сумке» в меню считаются по тому же правилу, что и изъятие
        assertEquals(4, qm().countInInventory(player, Material.OAK_LOG, true));
        assertEquals(14, qm().countInInventory(player, Material.OAK_LOG, false));

        plugin.getMenuManager().openTree(player);
        click(ClickType.LEFT, slotOf(1));

        assertEquals(4, qm().deposited(id, quest(1), Material.OAK_LOG), "забраться должны только обычные брёвна");
        assertEquals(10, count(Material.OAK_LOG), "именное бревно должно остаться у игрока");
    }

    @Test
    void questWithTwoMaterialsTakesBothAndFinishesOnlyWhenBothAreDone() {
        completeBefore(13);
        player.getInventory().addItem(new ItemStack(Material.ENDER_EYE, 12), new ItemStack(Material.BLAZE_ROD, 3));
        plugin.getMenuManager().openTree(player);

        click(ClickType.LEFT, slotOf(13));
        assertEquals(0, count(Material.ENDER_EYE));
        assertEquals(0, count(Material.BLAZE_ROD));
        assertEquals(13, qm().currentQuest(id).id, "жезлов ещё не хватает");
        assertEquals(15, qm().currentProgress(id, quest(13)));

        player.getInventory().addItem(new ItemStack(Material.BLAZE_ROD, 5));
        click(ClickType.LEFT, slotOf(13));
        assertEquals(2, count(Material.BLAZE_ROD), "нужно было 3, лишние 2 остаются");
        assertEquals(14, qm().currentQuest(id).id);
        ItemStack elytra = find(Material.ELYTRA);
        assertNotNull(elytra);
        assertEquals("Крылья Покорителя Края", nameOf(elytra));
    }

    @Test
    void craftQuestIsClaimedWithoutTakingTheTool() {
        completeBefore(2);
        player.getInventory().addItem(new ItemStack(Material.STONE_PICKAXE));
        plugin.getMenuManager().openTree(player);

        click(ClickType.LEFT, slotOf(2));

        assertEquals(1, count(Material.STONE_PICKAXE), "скрафченную кирку квест забирать не должен");
        assertEquals(25, count(Material.COBBLESTONE), "награда — 25 булыжника");
        assertEquals(3, qm().currentQuest(id).id);
    }

    // ---------------------------------------------------------------- меню

    @Test
    void treeShowsAllThirtyFiveQuestsAndEachClickOpensItsOwnQuest() {
        completeBefore(36); // все выполнены — на каждый квест можно кликнуть
        for (int questId = 1; questId <= 35; questId++) {
            plugin.getMenuManager().openTree(player);
            ItemStack icon = top().getItem(slotOf(questId));
            assertNotNull(icon, "в дереве нет квеста #" + questId);
            assertTrue(nameOf(icon).contains(plugin.getQuestRegistry().byId(questId).name), "не тот квест в слоте #" + questId);

            click(ClickType.RIGHT, slotOf(questId));
            tick();
            ItemStack card = top().getItem(4);
            assertNotNull(card);
            assertTrue(nameOf(card).contains("#" + questId + " ·"),
                    "клик по квесту #" + questId + " открыл: " + nameOf(card));
            assertEquals(quest(questId).iconMaterial(), card.getType());
        }
    }

    @Test
    void infoWindowIconsDifferPerQuestInsteadOfAlwaysOak() {
        Set<Material> icons = new HashSet<>();
        for (QuestDefinition q : plugin.getQuestRegistry().all()) {
            plugin.getMenuManager().openInfo(player, q);
            ItemStack card = top().getItem(4);
            assertEquals(q.iconMaterial(), card.getType());
            assertTrue(nameOf(card).contains("#" + q.id + " ·"));
            icons.add(card.getType());
        }
        assertTrue(icons.size() >= 25, "иконки почти одинаковые: " + icons.size());
        plugin.getMenuManager().openInfo(player, quest(12));
        assertEquals(Material.END_PORTAL_FRAME, top().getItem(4).getType());
        plugin.getMenuManager().openInfo(player, quest(24));
        assertEquals(Material.SUGAR_CANE, top().getItem(4).getType());
    }

    @Test
    void lockedQuestsAreNotClickableForInfo() {
        plugin.getMenuManager().openTree(player);
        click(ClickType.RIGHT, slotOf(6));
        tick();
        assertTrue(plain(player.getOpenInventory().title()).contains("Дерево квестов"),
                "закрытый квест не должен открывать окно информации");
    }

    @Test
    void infoWindowShowsRewardsAsRealEnchantedItems() {
        plugin.getMenuManager().openInfo(player, quest(1));
        boolean found = false;
        for (ItemStack s : top().getContents()) {
            if (s != null && s.getType() == Material.WOODEN_AXE) {
                found = true;
                assertEquals(2, s.getEnchantmentLevel(Enchantment.EFFICIENCY));
            }
        }
        assertTrue(found, "в окне информации нет предмета-награды");
    }

    @Test
    void menusCannotBeRobbedByClicking() {
        plugin.getMenuManager().openMain(player);
        InventoryClickEvent e = click(ClickType.SHIFT_LEFT, 22);
        assertTrue(e.isCancelled());
        plugin.getMenuManager().openTop(player);
        assertTrue(plain(player.getOpenInventory().title()).contains("Топ игроков"));
    }

    // ---------------------------------------------------------------- состояния мира

    @Test
    void enteringNetherCountsWithoutAdvancement() {
        completeBefore(9);
        WorldMock nether = new WorldMock() {
            @Override
            public World.Environment getEnvironment() {
                return World.Environment.NETHER;
            }
        };
        server.addWorld(nether);
        player.teleport(new Location(nether, 0, 64, 0));

        qm().poll(player); // игрок уже стоит в Нижнем мире — квест засчитывается

        assertTrue(plugin.getPlayerData().isCompleted(id, 9));
        assertEquals(2, count(Material.ENDER_PEARL), "награда — 2 жемчуга Края");
    }
}
