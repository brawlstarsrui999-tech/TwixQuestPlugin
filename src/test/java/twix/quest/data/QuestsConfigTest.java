package twix.quest.data;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import twix.quest.quest.QuestType;
import twix.quest.reward.Reward;
import twix.quest.reward.RewardItem;
import twix.quest.util.TextUtil;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Сверяет вшитый quests.yml со списком квестов и наград, согласованным с заказчиком.
 * Не требует сервера: проверяется только разбор файла.
 */
class QuestsConfigTest {

    /** Чар: ключ → уровень. */
    private static Map<String, Integer> ench(Object... kv) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Integer) kv[i + 1]);
        return m;
    }

    private record Item(String material, int amount, Map<String, Integer> enchants) {}

    private static Item item(String material, int amount, Map<String, Integer> enchants) {
        return new Item(material, amount, enchants);
    }

    private static Item item(String material, int amount) {
        return new Item(material, amount, Map.of());
    }

    private record Spec(int id, String type, int amount, String target, Map<String, Integer> extras,
                        List<Item> items, int vault, int twix, int exp) {}

    private static Spec spec(int id, String type, int amount, String target, Map<String, Integer> extras,
                             List<Item> items, int vault, int twix, int exp) {
        return new Spec(id, type, amount, target, extras, items, vault, twix, exp);
    }

    private static final List<Item> NONE = List.of();
    private static final Map<String, Integer> NO_EXTRAS = Map.of();

    /** Список заказчика: «квест → цель → награда». */
    private static final List<Spec> SPEC = List.of(
            spec(1, "DEPOSIT_ITEM", 10, "OAK_LOG", NO_EXTRAS, List.of(item("WOODEN_AXE", 1, ench("efficiency", 2))), 0, 0, 0),
            spec(2, "CRAFT_ITEM", 1, "STONE_PICKAXE", NO_EXTRAS, List.of(item("COBBLESTONE", 25)), 0, 0, 0),
            spec(3, "SELL_TO_BUYER", 1, null, NO_EXTRAS, NONE, 100, 0, 0),
            spec(4, "DEPOSIT_ITEM", 30, "DIORITE", NO_EXTRAS, List.of(item("STONE_PICKAXE", 1, ench("fortune", 1, "efficiency", 2))), 0, 0, 0),
            spec(5, "CRAFT_ITEM", 1, "IRON_PICKAXE", NO_EXTRAS, List.of(item("IRON_INGOT", 3)), 0, 0, 0),
            spec(6, "DEPOSIT_ITEM", 1, "DIAMOND", NO_EXTRAS, NONE, 0, 0, 0),
            spec(7, "KILL_MOB", 10, "ZOMBIE", NO_EXTRAS, List.of(item("DIAMOND_SWORD", 1, ench("sharpness", 2))), 0, 0, 0),
            spec(8, "CRAFT_ITEM", 1, "DIAMOND_PICKAXE", NO_EXTRAS, List.of(item("OBSIDIAN", 4)), 0, 0, 0),
            spec(9, "ENTER_WORLD", 1, "NETHER", NO_EXTRAS, List.of(item("ENDER_PEARL", 2)), 0, 0, 0),
            spec(10, "KILL_MOB", 5, "BLAZE", NO_EXTRAS, List.of(item("BLAZE_ROD", 2)), 0, 0, 0),
            spec(11, "DEPOSIT_ITEM", 3, "ENDER_EYE", NO_EXTRAS, List.of(item("DIAMOND_CHESTPLATE", 1, ench("protection", 4))), 0, 0, 0),
            spec(12, "ENTER_WORLD", 1, "THE_END", NO_EXTRAS, NONE, 1500, 0, 0),
            spec(13, "DEPOSIT_ITEM", 12, "ENDER_EYE", Map.of("BLAZE_ROD", 6), List.of(item("ELYTRA", 1)), 0, 0, 0),
            spec(14, "TRADE_VILLAGER", 1, null, NO_EXTRAS, NONE, 0, 0, 0),
            spec(15, "DEPOSIT_ITEM", 10, "EMERALD", NO_EXTRAS, List.of(item("ENCHANTED_BOOK", 2, ench("mending", 1))), 0, 0, 0),
            spec(16, "KILL_MOB", 3, "VILLAGER", NO_EXTRAS, List.of(item("EMERALD", 15)), 0, 0, 0),
            spec(17, "DEPOSIT_ITEM", 2, "TRIAL_KEY", NO_EXTRAS, List.of(item("OMINOUS_TRIAL_KEY", 1)), 0, 0, 0),
            spec(18, "KILL_MOB", 10, "SPIDER", NO_EXTRAS, NONE, 0, 0, 0),
            spec(19, "KILL_MOB", 25, "BREEZE", NO_EXTRAS, List.of(item("OMINOUS_TRIAL_KEY", 2)), 0, 0, 0),
            spec(20, "DEPOSIT_ITEM", 1, "HEAVY_CORE", NO_EXTRAS,
                    List.of(item("MACE", 1), item("ENCHANTED_BOOK", 1, ench("wind_burst", 3))), 0, 0, 0),
            spec(21, "REACH_BALANCE", 3500, null, NO_EXTRAS, List.of(item("GOLD_BLOCK", 4)), 0, 0, 0),
            spec(22, "DEPOSIT_ITEM", 750, "COBBLESTONE", NO_EXTRAS,
                    List.of(item("DIAMOND_PICKAXE", 1, ench("fortune", 3, "efficiency", 5, "unbreaking", 3, "mending", 1))), 0, 0, 0),
            spec(23, "DEPOSIT_ITEM", 25, "DIAMOND", NO_EXTRAS, NONE, 0, 0, 0),
            spec(24, "SELL_TO_BUYER", 250, "SUGAR_CANE", NO_EXTRAS, NONE, 1500, 0, 0),
            spec(25, "KILL_PLAYER", 1, null, NO_EXTRAS,
                    List.of(item("DIAMOND_SWORD", 1, ench("sharpness", 4, "fire_aspect", 1, "unbreaking", 2))), 0, 0, 0),
            spec(26, "KILL_MOB", 1, "IRON_GOLEM", NO_EXTRAS, List.of(item("IRON_INGOT", 30)), 0, 0, 0),
            spec(27, "MOUNT_STRIDER", 1, null, NO_EXTRAS, List.of(item("ANCIENT_DEBRIS", 1)), 0, 0, 0),
            spec(28, "DEPOSIT_ITEM", 200, "QUARTZ", NO_EXTRAS, NONE, 0, 0, 30),
            spec(29, "DEPOSIT_ITEM", 25, "GOLD_INGOT", NO_EXTRAS, NONE, 0, 0, 0),
            spec(30, "DEPOSIT_ITEM", 5, "NETHERITE_INGOT", NO_EXTRAS, NONE, 0, 50, 0),
            spec(31, "VISIT_STRUCTURE", 1, "bastion_remnant", NO_EXTRAS, NONE, 0, 0, 0),
            spec(32, "KILL_MOB", 3, "WITHER_SKELETON", NO_EXTRAS, List.of(item("WITHER_SKELETON_SKULL", 1)), 0, 0, 0),
            spec(33, "DEPOSIT_ITEM", 150, "SOUL_SAND", NO_EXTRAS, NONE, 0, 0, 0),
            spec(34, "KILL_MOB", 10, "WITHER_SKELETON", NO_EXTRAS, NONE, 0, 0, 0),
            spec(35, "DEPOSIT_ITEM", 1, "NETHER_STAR", NO_EXTRAS, NONE, 0, 25, 0)
    );

    /** Ресурсы: им нельзя давать имя (не стакаются, не подойдут для вложения, ключ не откроет хранилище). */
    private static final Set<String> PLAIN_RESOURCES = Set.of(
            "COBBLESTONE", "IRON_INGOT", "OBSIDIAN", "ENDER_PEARL", "BLAZE_ROD", "EMERALD",
            "OMINOUS_TRIAL_KEY", "GOLD_BLOCK", "ANCIENT_DEBRIS", "WITHER_SKELETON_SKULL");

    /** Настоящие ключи чар Minecraft, которые вообще допустимы в наградах. */
    private static final Set<String> VANILLA_ENCHANTS = Set.of(
            "protection", "fire_protection", "feather_falling", "blast_protection", "projectile_protection",
            "respiration", "aqua_affinity", "thorns", "depth_strider", "frost_walker", "soul_speed", "swift_sneak",
            "sharpness", "smite", "bane_of_arthropods", "knockback", "fire_aspect", "looting", "sweeping_edge",
            "efficiency", "silk_touch", "unbreaking", "fortune", "power", "punch", "flame", "infinity",
            "luck_of_the_sea", "lure", "loyalty", "impaling", "riptide", "channeling", "multishot", "quick_charge",
            "piercing", "density", "breach", "wind_burst", "mending");

    private static YamlConfiguration yml;

    @BeforeAll
    static void load() {
        // Реестры Bukkit (Material, Enchantment...) без сервера не инициализируются.
        MockBukkit.mock();
        yml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                QuestsConfigTest.class.getResourceAsStream("/quests.yml"), StandardCharsets.UTF_8));
        TextUtil.init(null);
    }

    @AfterAll
    static void unload() {
        MockBukkit.unmock();
    }

    private static ConfigurationSection quest(int id) {
        ConfigurationSection s = yml.getConfigurationSection("quests." + id);
        assertNotNull(s, "квеста #" + id + " нет в quests.yml");
        return s;
    }

    private static Reward reward(int id) {
        return RewardBuilder.from(quest(id).getConfigurationSection("reward"), null, id);
    }

    @Test
    void thirtyFiveQuestsWithoutGaps() {
        ConfigurationSection all = yml.getConfigurationSection("quests");
        assertNotNull(all);
        assertEquals(35, all.getKeys(false).size());
        for (int i = 1; i <= 35; i++) assertTrue(all.contains(String.valueOf(i)), "нет квеста #" + i);
        assertTrue(yml.getInt("config-version") >= 4, "версия файла нужна для автообновления на сервере");
    }

    @Test
    void everyQuestMatchesTheAgreedList() {
        for (Spec s : SPEC) {
            ConfigurationSection q = quest(s.id());
            String where = "квест #" + s.id() + ": ";

            assertEquals(s.type(), q.getString("type"), where + "тип");
            assertEquals(s.amount(), q.getInt("amount"), where + "количество");

            String target = switch (QuestType.valueOf(s.type())) {
                case DEPOSIT_ITEM, CRAFT_ITEM, SELL_TO_BUYER, MINE_BLOCK -> q.getString("material");
                case KILL_MOB -> q.getString("entity-type");
                case ENTER_WORLD -> q.getString("world-env");
                case VISIT_STRUCTURE -> q.getString("structure");
                default -> null;
            };
            assertEquals(s.target(), target, where + "цель");

            Map<String, Integer> extras = new LinkedHashMap<>();
            for (Object o : q.getList("extras", List.of())) {
                Map<String, Object> m = RewardBuilder.asMap(o);
                extras.put(String.valueOf(m.get("material")), ((Number) m.get("amount")).intValue());
            }
            assertEquals(s.extras(), extras, where + "дополнительные предметы");

            Reward r = reward(s.id());
            assertEquals(s.items().size(), r.items.size(), where + "число предметов в награде");
            for (int i = 0; i < s.items().size(); i++) {
                Item expected = s.items().get(i);
                RewardItem actual = r.items.get(i);
                assertEquals(expected.material(), actual.material.name(), where + "предмет награды #" + (i + 1));
                assertEquals(expected.amount(), actual.amount, where + "количество предмета награды #" + (i + 1));
                Map<String, Integer> got = new LinkedHashMap<>();
                for (RewardItem.Entry e : actual.enchantments) got.put(e.key, e.level);
                assertEquals(expected.enchants(), got, where + "чары предмета награды #" + (i + 1));
            }
            assertEquals(s.vault(), r.vaultCoins, where + "монеты");
            assertEquals(s.twix(), r.twixCoins, where + "Twixcoin");
            assertEquals(s.exp(), r.expLevels, where + "уровни опыта");
        }
    }

    /**
     * Регрессия: раньше чары терялись — сырая Map внутри списка предметов читалась через
     * getConfigurationSection и давала null, поэтому ВСЕ предметы выдавались без чар.
     */
    @Test
    void enchantmentsFromListItemsAreNotLost() {
        int enchanted = 0;
        for (Spec s : SPEC) {
            Reward r = reward(s.id());
            for (int i = 0; i < s.items().size(); i++) {
                if (s.items().get(i).enchants().isEmpty()) continue;
                enchanted++;
                assertFalse(r.items.get(i).enchantments.isEmpty(),
                        "квест #" + s.id() + ": чары награды потерялись при разборе");
            }
        }
        assertEquals(9, enchanted, "ожидали 9 зачарованных наград (квесты 1, 4, 7, 11, 15, 20, 22, 25)... проверьте список");
    }

    @Test
    void allEnchantmentKeysAreRealMinecraftEnchantments() {
        for (int id = 1; id <= 35; id++) {
            for (RewardItem item : reward(id).items) {
                for (RewardItem.Entry e : item.enchantments) {
                    assertTrue(VANILLA_ENCHANTS.contains(e.key), "квест #" + id + ": неизвестный чар '" + e.key + "'");
                    assertTrue(e.level >= 1 && e.level <= 10, "квест #" + id + ": странный уровень чара " + e.level);
                }
            }
        }
    }

    @Test
    void targetsAreRealGameObjects() {
        for (Spec s : SPEC) {
            ConfigurationSection q = quest(s.id());
            String mat = q.getString("material");
            if (mat != null) assertNotNull(Material.matchMaterial(mat), "квест #" + s.id() + ": нет материала " + mat);
            String icon = q.getString("icon");
            if (icon != null) assertNotNull(Material.matchMaterial(icon), "квест #" + s.id() + ": нет иконки " + icon);
            String entity = q.getString("entity-type");
            if (entity != null) EntityType.valueOf(entity); // бросит исключение, если такого моба нет
            for (Object o : q.getList("extras", List.of())) {
                String m = String.valueOf(RewardBuilder.asMap(o).get("material"));
                assertNotNull(Material.matchMaterial(m), "квест #" + s.id() + ": нет материала " + m);
            }
        }
    }

    @Test
    void resourceRewardsStayVanillaAndGearIsNamed() {
        List<String> named = new ArrayList<>();
        for (int id = 1; id <= 35; id++) {
            for (RewardItem item : reward(id).items) {
                String m = item.material.name();
                if (PLAIN_RESOURCES.contains(m)) {
                    assertNull(item.name, "квест #" + id + ": ресурс " + m + " не должен иметь своё имя");
                } else {
                    assertTrue(item.hasCustomName(), "квест #" + id + ": у " + m + " нет красивого названия");
                    named.add(PlainTextComponentSerializer.plainText().serialize(TextUtil.mm(item.name)));
                }
            }
        }
        assertEquals(List.of(
                "Топор Дровосека", "Кирка Рудокопа", "Клинок Ночного Стража", "Доспех Искателя Края",
                "Крылья Покорителя Края", "Книга Вечной Починки", "Булава Громовержца", "Книга Порыва Ветра",
                "Кирка Золотой Жилы", "Клинок Пламенного Дуэлянта"), named,
                "имена наград должны разбираться в чистый текст (без остатков тегов)");
    }

    @Test
    void textsAreFilledAndTooltipFriendly() {
        for (int id = 1; id <= 35; id++) {
            ConfigurationSection q = quest(id);
            String name = q.getString("name", "");
            String description = q.getString("description", "");
            assertFalse(name.isBlank(), "квест #" + id + ": пустое название");
            assertFalse(description.isBlank(), "квест #" + id + ": пустое описание");
            assertTrue(name.length() <= 32, "квест #" + id + ": слишком длинное название");
            assertTrue(description.length() <= 150, "квест #" + id + ": слишком длинное описание (" + description.length() + ")");
            assertFalse(name.contains("<") || description.contains("<"), "квест #" + id + ": в описании не должно быть тегов");
        }
    }

    @Test
    void questsDoNotUseFragileAchievementBasedChecks() {
        // «Зайти в Ад» и «в Энд» проверяются сменой мира, а не достижением.
        assertEquals("ENTER_WORLD", quest(9).getString("type"));
        assertEquals("ENTER_WORLD", quest(12).getString("type"));
        assertFalse(yml.saveToString().toLowerCase().contains("advancement"));
    }
}
