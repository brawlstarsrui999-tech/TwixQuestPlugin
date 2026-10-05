package twix.quest.quest;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.generator.structure.Structure;
import org.bukkit.inventory.ItemStack;
import twix.quest.reward.Reward;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Иммутабельное определение квеста: тип, цель, вознаграждение, декоративные параметры. */
public final class QuestDefinition {

    public final int id;
    public final String name;
    public final String description;
    public final QuestType type;
    public final int amount;
    public final Material itemMaterial;
    public final String keyword;
    public final EntityType entityType;
    public final World.Environment worldEnv;
    public final String worldName;
    public final Structure structure;
    public final int structureRadius;
    public final Reward reward;
    public final boolean allowDepositLeftover;
    public final boolean silent;
    /** Для квестов байера: засчитывать и встречную операцию (купил вместо продал и наоборот). */
    public final boolean countBuys;
    /**
     * Забирать ли предметы при клике на квест. Для {@code DEPOSIT_ITEM} по умолчанию да,
     * для {@code CRAFT_ITEM} — нет (кирка, которую вы только что скрафтили, вам ещё нужна).
     */
    public final boolean consume;
    /** Иконка из quests.yml ({@code icon: DIAMOND}); {@code null} — подбирается по типу квеста. */
    public final Material iconOverride;

    /** Дополнительные требования для квестов с вложением нескольких материалов. */
    public final List<MaterialRequirement> extras;

    /** Итоговый список «что нужно принести»: основной материал + extras. Пуст у квестов без предметов. */
    private final List<MaterialRequirement> requirements;

    public QuestDefinition(int id, String name, String description, QuestType type, int amount,
                           Material itemMaterial, String keyword, EntityType entityType,
                           World.Environment worldEnv, String worldName, Structure structure,
                           int structureRadius, Reward reward, boolean allowDepositLeftover, boolean silent,
                           boolean countBuys, boolean consume, Material iconOverride,
                           List<MaterialRequirement> extras) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.amount = amount;
        this.itemMaterial = itemMaterial;
        this.keyword = keyword;
        this.entityType = entityType;
        this.worldEnv = worldEnv;
        this.worldName = worldName;
        this.structure = structure;
        this.structureRadius = structureRadius;
        this.reward = reward;
        this.allowDepositLeftover = allowDepositLeftover;
        this.silent = silent;
        this.countBuys = countBuys;
        this.consume = consume;
        this.iconOverride = iconOverride;
        this.extras = extras == null ? List.of() : extras;

        List<MaterialRequirement> reqs = new ArrayList<>();
        if (type == QuestType.DEPOSIT_ITEM) {
            if (itemMaterial != null) reqs.add(new MaterialRequirement(itemMaterial, amount));
            reqs.addAll(this.extras);
        } else if (type == QuestType.CRAFT_ITEM && itemMaterial != null) {
            reqs.add(new MaterialRequirement(itemMaterial, amount));
        }
        this.requirements = List.copyOf(reqs);
    }

    public static Material m(String s) {
        if (s == null) return null;
        return Material.matchMaterial(s.trim().toUpperCase(Locale.ROOT));
    }

    public static EntityType et(String s) {
        if (s == null) return null;
        try { return EntityType.valueOf(s.trim().toUpperCase(Locale.ROOT)); } catch (Exception e) { return null; }
    }

    /** Предметы, которые можно «вложить»/«предъявить» кликом по квесту. Пусто — квест выполняется действием. */
    public List<MaterialRequirement> requirements() {
        return requirements;
    }

    /** По клику на такой квест игрок может вложить предметы. */
    public boolean isClaimable() {
        return !requirements.isEmpty();
    }

    /** Общий «объём» квеста: для вложений — сумма по всем материалам, для остальных — {@code amount}. */
    public int totalAmount() {
        if (type != QuestType.DEPOSIT_ITEM) return amount;
        int sum = 0;
        for (MaterialRequirement r : requirements) sum += r.amount;
        return Math.max(1, sum);
    }

    public String shortType() {
        return switch (type) {
            case MINE_BLOCK       -> "Добыча блоков";
            case CRAFT_ITEM       -> "Создание предмета";
            case RECEIVE_ITEM     -> "Получение предмета";
            case KILL_MOB         -> "Охота";
            case KILL_PLAYER      -> "Дуэль (PvP)";
            case ENTER_WORLD      -> "Путешествие";
            case VISIT_STRUCTURE  -> "Исследование";
            case TRADE_VILLAGER   -> "Торговля";
            case SELL_TO_BUYER    -> "Продажа байеру";
            case BUY_FROM_BUYER   -> "Покупка у байера";
            case REACH_BALANCE    -> "Накопление монет";
            case DEPOSIT_ITEM     -> "Вложить предметы";
            case MOUNT_STRIDER    -> "Верховая езда";
        };
    }

    public ItemStack icon() {
        return new ItemStack(iconMaterial());
    }

    /** Материал иконки: у каждого квеста свой, а не один и тот же «по умолчанию». */
    public Material iconMaterial() {
        if (iconOverride != null) return iconOverride;
        return switch (type) {
            case MINE_BLOCK, CRAFT_ITEM, RECEIVE_ITEM, DEPOSIT_ITEM ->
                    itemMaterial != null ? itemMaterial : Material.CHEST;
            case KILL_MOB        -> spawnEgg(entityType);
            case KILL_PLAYER     -> Material.PLAYER_HEAD;
            case ENTER_WORLD     -> worldEnv == World.Environment.NETHER ? Material.NETHERRACK
                                  : worldEnv == World.Environment.THE_END ? Material.END_STONE
                                  : Material.COMPASS;
            case VISIT_STRUCTURE -> Material.SPYGLASS;
            case TRADE_VILLAGER  -> Material.EMERALD;
            case SELL_TO_BUYER   -> itemMaterial != null ? itemMaterial : Material.GOLD_INGOT;
            case BUY_FROM_BUYER  -> itemMaterial != null ? itemMaterial : Material.EMERALD;
            case REACH_BALANCE   -> Material.GOLD_BLOCK;
            case MOUNT_STRIDER   -> Material.WARPED_FUNGUS_ON_A_STICK;
        };
    }

    /** Яйцо призыва нужного моба; для мобов без яйца (железный голем) — меч. */
    private static Material spawnEgg(EntityType t) {
        if (t != null) {
            Material egg = Material.matchMaterial(t.name() + "_SPAWN_EGG");
            if (egg != null) return egg;
        }
        return Material.DIAMOND_SWORD;
    }

    /** Требование по материалу: «столько-то штук такого-то предмета». */
    public static final class MaterialRequirement {
        public final Material material;
        public final int amount;
        public MaterialRequirement(Material m, int a) { this.material = m; this.amount = a; }

        public static List<MaterialRequirement> of(Object... pairs) {
            if (pairs == null || pairs.length == 0) return List.of();
            List<MaterialRequirement> list = new ArrayList<>();
            for (int i = 0; i + 1 < pairs.length; i += 2) {
                list.add(new MaterialRequirement((Material) pairs[i], (int) pairs[i + 1]));
            }
            return list;
        }
    }
}
