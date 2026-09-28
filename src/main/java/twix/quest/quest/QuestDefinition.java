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

    /** Дополнительные требования для квестов с депозитом/добычей нескольких материалов. */
    public final List<MaterialRequirement> extras;

    public QuestDefinition(int id, String name, String description, QuestType type, int amount,
                           Material itemMaterial, String keyword, EntityType entityType,
                           World.Environment worldEnv, String worldName, Structure structure,
                           int structureRadius, Reward reward, boolean allowDepositLeftover, boolean silent,
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
        this.extras = extras == null ? List.of() : extras;
    }

    public static Material m(String s) {
        if (s == null) return null;
        return Material.matchMaterial(s.trim().toUpperCase(Locale.ROOT));
    }

    public static EntityType et(String s) {
        if (s == null) return null;
        try { return EntityType.valueOf(s.trim().toUpperCase(Locale.ROOT)); } catch (Exception e) { return null; }
    }

    public String shortType() {
        return switch (type) {
            case MINE_BLOCK       -> "Добыча";
            case CRAFT_ITEM       -> "Скрафтить";
            case RECEIVE_ITEM     -> "Получить";
            case KILL_MOB         -> "Убийство";
            case KILL_PLAYER      -> "Убийство игрока";
            case ENTER_WORLD      -> "Перейти в мир";
            case VISIT_STRUCTURE  -> "Посетить структуру";
            case TRADE_VILLAGER   -> "Торговля";
            case SELL_TO_BUYER    -> "Продажа байеру";
            case REACH_BALANCE    -> "Баланс";
            case DEPOSIT_ITEM     -> "Вложить";
            case MOUNT_STRIDER    -> "Оседлать лавомерку";
        };
    }

    public ItemStack icon() {
        Material mat = switch (type) {
            case MINE_BLOCK, DEPOSIT_ITEM, CRAFT_ITEM -> itemMaterial != null ? itemMaterial : Material.CHEST;
            case RECEIVE_ITEM                          -> itemMaterial != null ? itemMaterial : Material.STONE;
            case KILL_MOB, KILL_PLAYER                 -> Material.DIAMOND_SWORD;
            case ENTER_WORLD                            -> Material.ENDER_PEARL;
            case VISIT_STRUCTURE                        -> Material.SPYGLASS;
            case TRADE_VILLAGER                         -> Material.EMERALD;
            case SELL_TO_BUYER                          -> Material.GOLD_INGOT;
            case REACH_BALANCE                          -> Material.GOLD_BLOCK;
            case MOUNT_STRIDER                          -> Material.STRIDER_SPAWN_EGG;
        };
        return new ItemStack(mat);
    }

    /** Требование по дополнительным материалам. */
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
