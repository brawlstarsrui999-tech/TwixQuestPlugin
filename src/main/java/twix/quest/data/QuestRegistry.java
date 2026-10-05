package twix.quest.data;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import twix.quest.quest.QuestDefinition;
import twix.quest.quest.QuestType;
import twix.quest.reward.Reward;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Реестр квестов, грузится из {@code quests.yml}, полностью настраиваемый. */
public final class QuestRegistry {

    private final JavaPlugin plugin;
    private final List<QuestDefinition> quests = new ArrayList<>();
    private File file;
    private YamlConfiguration yml;

    public QuestRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public int size() { return quests.size(); }
    public List<QuestDefinition> all() { return Collections.unmodifiableList(quests); }

    public QuestDefinition byId(int id) {
        for (QuestDefinition q : quests) if (q.id == id) return q;
        return null;
    }

    public int indexOf(int id) {
        for (int i = 0; i < quests.size(); i++) {
            if (quests.get(i).id == id) return i;
        }
        return -1;
    }

    public QuestDefinition firstUncompleted(UUID uuid, PlayerQuestData data) {
        for (QuestDefinition q : quests) {
            if (!data.isCompleted(uuid, q.id)) return q;
        }
        return null;
    }

    /** Полная перезагрузка из файла. */
    public void loadAll() {
        quests.clear();
        this.file = new File(plugin.getDataFolder(), "quests.yml");
        if (!file.exists()) {
            plugin.saveResource("quests.yml", false);
        } else {
            upgradeFileIfOutdated();
        }
        yml = YamlConfiguration.loadConfiguration(file);

        // Дополняем дефолтами из jar, если отсутствуют разделы
        InputStream def = plugin.getResource("quests.yml");
        if (def != null) {
            YamlConfiguration defYml = YamlConfiguration.loadConfiguration(new InputStreamReader(def, StandardCharsets.UTF_8));
            for (String key : defYml.getKeys(false)) {
                if (!yml.contains(key)) {
                    yml.set(key, defYml.get(key));
                }
            }
        }

        ConfigurationSection sec = yml.getConfigurationSection("quests");
        if (sec == null) {
            plugin.getLogger().warning("Секция quests отсутствует в quests.yml — никаких квестов не загружено.");
            return;
        }
        int loaded = 0;
        for (String idStr : sec.getKeys(false)) {
            int id;
            try { id = Integer.parseInt(idStr); } catch (NumberFormatException ex) { continue; }
            ConfigurationSection q = sec.getConfigurationSection(idStr);
            if (q == null) continue;
            try {
                QuestDefinition def1 = parseQuest(id, q);
                if (def1 != null) {
                    quests.add(def1);
                    loaded++;
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("Не удалось загрузить квест #" + id + ": " + t.getMessage());
            }
        }
        // Сортируем по id, чтобы очередь была честной
        quests.sort(Comparator.comparingInt(q -> q.id));
        plugin.getLogger().info("Загружено квестов: " + loaded);
    }

    private QuestDefinition parseQuest(int id, ConfigurationSection q) {
        String name        = q.getString("name", "Квест #" + id);
        String description = q.getString("description", "Описание квеста");
        QuestType type     = QuestType.valueOf(q.getString("type", "MINE_BLOCK").toUpperCase(Locale.ROOT));
        int amount         = q.getInt("amount", 1);
        String matName     = q.getString("material", null);
        Material material  = matName == null ? null : Material.matchMaterial(matName.toUpperCase(Locale.ROOT));
        if (matName != null && material == null) plugin.getLogger().warning("Неизвестный материал '" + matName + "' для квеста #" + id);

        String worldName = q.getString("world-name", null);
        World.Environment env = null;
        String envStr = q.getString("world-env", null);
        if (envStr != null) try { env = World.Environment.valueOf(envStr.toUpperCase(Locale.ROOT)); } catch (Exception ignored) {}

        org.bukkit.entity.EntityType entityType = QuestDefinition.et(q.getString("entity-type", null));

        org.bukkit.generator.structure.Structure structure = null;
        String structName = q.getString("structure", null);
        if (structName != null) {
            try {
                org.bukkit.NamespacedKey key;
                if (structName.contains(":")) key = org.bukkit.NamespacedKey.fromString(structName);
                else key = org.bukkit.NamespacedKey.minecraft(structName.toLowerCase(java.util.Locale.ROOT));
                if (key != null) {
                    structure = org.bukkit.Registry.STRUCTURE.get(key);
                    if (structure == null) {
                        // Современный путь: RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE)
                        org.bukkit.generator.structure.Structure s = io.papermc.paper.registry.RegistryAccess.registryAccess()
                                .getRegistry(io.papermc.paper.registry.RegistryKey.STRUCTURE)
                                .get(key);
                        if (s != null) structure = s;
                    }
                }
            } catch (Throwable ignored) {}
        }

        ConfigurationSection rs = q.getConfigurationSection("reward");
        Reward reward = RewardBuilder.from(rs, plugin, id);
        boolean allowLeftover = q.getBoolean("allow-leftover", true);
        boolean silent = q.getBoolean("silent", false);
        // для квестов байера: считать ли встречную операцию (купил/продал)
        boolean countBuys = q.getBoolean("count-buys", false);
        // забирать ли предметы при клике на квест (вложение — да, «скрафтить» — нет)
        boolean consume = q.getBoolean("consume", type == QuestType.DEPOSIT_ITEM);
        // необязательная иконка квеста в меню
        Material iconOverride = QuestDefinition.m(q.getString("icon", null));

        int structureRadius = q.getInt("structure-radius", 256);

        List<QuestDefinition.MaterialRequirement> extras = new ArrayList<>();
        if (q.isList("extras")) {
            for (Object o : q.getList("extras")) {
                if (o instanceof ConfigurationSection es) {
                    Material m = QuestDefinition.m(es.getString("material"));
                    int amt = es.getInt("amount", 1);
                    if (m != null) extras.add(new QuestDefinition.MaterialRequirement(m, amt));
                } else if (o instanceof java.util.Map<?, ?> map) {
                    Material m = QuestDefinition.m(String.valueOf(map.get("material")));
                    int amt = 1;
                    Object a = map.get("amount");
                    if (a instanceof Number n) amt = n.intValue();
                    if (m != null) extras.add(new QuestDefinition.MaterialRequirement(m, amt));
                }
            }
        }

        QuestDefinition def = new QuestDefinition(id, name, description, type, amount, material,
                q.getString("keyword", null), entityType, env, worldName, structure, structureRadius,
                reward, allowLeftover, silent, countBuys, consume, iconOverride, extras);
        validate(def, structName);
        return def;
    }

    /** Предупреждает в консоли о квестах, которые невозможно выполнить из-за опечатки в quests.yml. */
    private void validate(QuestDefinition d, String structName) {
        String where = "Квест #" + d.id + ": ";
        switch (d.type) {
            case DEPOSIT_ITEM -> {
                if (d.requirements().isEmpty()) plugin.getLogger().warning(where + "тип DEPOSIT_ITEM без material — квест невыполним.");
            }
            case CRAFT_ITEM, MINE_BLOCK -> {
                if (d.itemMaterial == null) plugin.getLogger().warning(where + "не указан material — квест невыполним.");
            }
            case KILL_MOB -> {
                if (d.entityType == null) plugin.getLogger().warning(where + "не указан/неизвестен entity-type — квест невыполним.");
            }
            case ENTER_WORLD -> {
                if (d.worldEnv == null && d.worldName == null) plugin.getLogger().warning(where + "нужен world-env или world-name — квест невыполним.");
            }
            case VISIT_STRUCTURE -> {
                if (d.structure == null) plugin.getLogger().warning(where + "структура '" + structName + "' не найдена — квест невыполним.");
            }
            default -> { }
        }
        if (d.amount <= 0) plugin.getLogger().warning(where + "amount должен быть больше 0.");
    }

    // ------------------------------------------------------------------
    // Обновление quests.yml на сервере
    // ------------------------------------------------------------------

    /** Версия quests.yml, вшитого в jar ({@code config-version}). */
    private int bundledVersion() {
        InputStream in = plugin.getResource("quests.yml");
        if (in == null) return 0;
        return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8))
                .getInt("config-version", 0);
    }

    /**
     * Плагин копирует quests.yml в папку сервера один раз. Поэтому после обновления jar
     * на сервере оставался СТАРЫЙ файл — и новые квесты, награды и описания не появлялись.
     * Теперь, если версия файла ниже вшитой, старый файл сохраняется рядом как
     * {@code quests.yml.bak-...}, а на его место кладётся новый.
     */
    private void upgradeFileIfOutdated() {
        int have = YamlConfiguration.loadConfiguration(file).getInt("config-version", 0);
        int want = bundledVersion();
        if (have >= want) return;
        try {
            String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date());
            File backup = new File(plugin.getDataFolder(), "quests.yml.bak-v" + have + "-" + stamp);
            java.nio.file.Files.copy(file.toPath(), backup.toPath());
            plugin.saveResource("quests.yml", true);
            plugin.getLogger().warning("quests.yml обновлён с версии " + have + " до " + want
                    + ". Старый файл сохранён как " + backup.getName() + " — свои правки можно перенести из него.");
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().warning("Не удалось обновить quests.yml (версия " + have + " < " + want + "): " + ex.getMessage());
        }
    }

    /**
     * Список строк из секции {@code settings} quests.yml.
     * Используется хуком байера (заголовки GUI и метки команд).
     */
    public List<String> settingsList(String key, List<String> defaults) {
        if (yml == null) return defaults;
        ConfigurationSection s = yml.getConfigurationSection("settings");
        if (s == null || !s.isList(key)) return defaults;
        List<String> out = new ArrayList<>();
        for (String v : s.getStringList(key)) {
            if (v != null && !v.isBlank()) out.add(v.trim().toLowerCase(Locale.ROOT));
        }
        return out.isEmpty() ? defaults : out;
    }

    /** Сохранение кастомных правок. */
    public void save() {
        if (yml != null) {
            try { yml.save(file); } catch (IOException ignored) {}
        }
    }
}
