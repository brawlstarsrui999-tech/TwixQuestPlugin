package twix.quest.data;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import twix.quest.quest.QuestDefinition;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class PlayerQuestData {

    private final JavaPlugin plugin;
    private final File dataFolder;
    private final Map<UUID, YamlConfiguration> caches = new HashMap<>();

    /** Запомнить старт прохождения по каждому игроку для топа скорости. */
    private final Map<UUID, Long> startTimes = new HashMap<>();

    public PlayerQuestData(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!dataFolder.exists()) dataFolder.mkdirs();
    }

    public YamlConfiguration loadFile(UUID uuid) {
        YamlConfiguration cfg = caches.get(uuid);
        if (cfg != null) return cfg;
        File f = new File(dataFolder, uuid + ".yml");
        if (!f.exists()) {
            try { f.createNewFile(); } catch (IOException ignored) {}
        }
        cfg = YamlConfiguration.loadConfiguration(f);
        caches.put(uuid, cfg);
        return cfg;
    }

    private File getFile(UUID uuid) { return new File(dataFolder, uuid + ".yml"); }

    public boolean isCompleted(UUID uuid, int questId) {
        ConfigurationSection sec = loadFile(uuid).getConfigurationSection("completed");
        return sec != null && sec.contains(String.valueOf(questId));
    }

    public long completedAt(UUID uuid, int questId) {
        ConfigurationSection sec = loadFile(uuid).getConfigurationSection("completed");
        if (sec == null) return 0L;
        return sec.getLong(String.valueOf(questId));
    }

    public void setCompleted(UUID uuid, int questId) {
        YamlConfiguration cfg = loadFile(uuid);
        ConfigurationSection sec = cfg.getConfigurationSection("completed");
        if (sec == null) sec = cfg.createSection("completed");
        sec.set(String.valueOf(questId), System.currentTimeMillis());
        save(uuid);
    }

    public void reset(UUID uuid) {
        YamlConfiguration cfg = loadFile(uuid);
        cfg.set("completed", null);
        cfg.set("started", null);
        caches.put(uuid, cfg);
        save(uuid);
    }

    public long startTime(UUID uuid) {
        YamlConfiguration cfg = loadFile(uuid);
        long t = cfg.getLong("started", 0L);
        if (t == 0L) {
            t = System.currentTimeMillis();
            cfg.set("started", t);
            save(uuid);
        }
        return t;
    }

    public void save(UUID uuid) {
        YamlConfiguration cfg = caches.get(uuid);
        if (cfg == null) return;
        try {
            cfg.save(getFile(uuid));
        } catch (IOException e) {
            plugin.getLogger().warning("Не получилось сохранить данные игрока " + uuid + ": " + e.getMessage());
        }
    }

    public int countMainCompleted(UUID uuid, QuestRegistry registry) {
        int c = 0;
        for (QuestDefinition q : registry.all()) {
            if (isCompleted(uuid, q.id)) c++;
        }
        return c;
    }

    /**
     * Строка топа: кто, сколько квестов закрыл и за какое время.
     *
     * @param completed  количество пройденных основных квестов
     * @param durationMs время от старта прохождения до последнего закрытого квеста
     */
    public record TopEntry(UUID uuid, String name, int completed, long durationMs) {}

    /**
     * Топ игроков по основным квестам.
     *
     * <p>Сортировка: сначала по количеству пройденных квестов (по убыванию),
     * при равенстве — по времени прохождения (кто быстрее, тот выше).
     * В топ попадают все, кто закрыл хотя бы один квест: раньше список
     * требовал 100% прохождения, поэтому меню топа всегда было пустым.</p>
     *
     * @param limit максимум записей; &lt;= 0 — вернуть всех
     */
    public List<TopEntry> getTop(int limit) {
        Set<UUID> ids = new LinkedHashSet<>();
        File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File f : files) {
                String s = f.getName();
                if (s.length() <= 4) continue;
                s = s.substring(0, s.length() - 4);
                try {
                    ids.add(UUID.fromString(s));
                } catch (IllegalArgumentException notAUuid) {
                    // не наш файл (data.yml.lock и т.п.) — пропускаем
                }
            }
        }
        // Кэш в памяти актуальнее файла на диске (онлайновые игроки).
        ids.addAll(caches.keySet());

        List<TopEntry> entries = new ArrayList<>();
        for (UUID id : ids) {
            try {
                YamlConfiguration c = caches.get(id);
                if (c == null) {
                    File f = new File(dataFolder, id + ".yml");
                    if (!f.exists()) continue;
                    c = YamlConfiguration.loadConfiguration(f);
                }
                ConfigurationSection cs = c.getConfigurationSection("completed");
                if (cs == null) continue;

                int completed = 0;
                long latest = 0L;
                long earliest = Long.MAX_VALUE;
                for (String k : cs.getKeys(false)) {
                    long ts = cs.getLong(k);
                    if (ts <= 0L) continue;
                    completed++;
                    if (ts > latest) latest = ts;
                    if (ts < earliest) earliest = ts;
                }
                if (completed <= 0) continue;

                long started = c.getLong("started", 0L);
                // У старых файлов поля "started" может не быть — считаем от первого квеста.
                if (started <= 0L) started = earliest == Long.MAX_VALUE ? latest : earliest;
                long duration = Math.max(0L, latest - started);

                entries.add(new TopEntry(id, resolveName(id, c), completed, duration));
            } catch (Exception ex) {
                if (plugin != null) {
                    plugin.getLogger().warning("Не удалось прочитать playerdata " + id + ": " + ex.getMessage());
                }
            }
        }

        entries.sort(Comparator.<TopEntry>comparingInt(TopEntry::completed).reversed()
                .thenComparingLong(TopEntry::durationMs)
                .thenComparing(TopEntry::name, String.CASE_INSENSITIVE_ORDER));

        if (limit > 0 && entries.size() > limit) return new ArrayList<>(entries.subList(0, limit));
        return entries;
    }

    /** Имя для топа: last-known-name → онлайновый игрок → оффлайн-профиль → 8 символов UUID. */
    private String resolveName(UUID id, YamlConfiguration c) {
        String name = c.getString("last-known-name");
        if (name != null && !name.isBlank()) return name;
        try {
            Player online = Bukkit.getPlayer(id);
            if (online != null) return online.getName();
            OfflinePlayer off = Bukkit.getOfflinePlayer(id);
            String offName = off.getName();
            if (offName != null && !offName.isBlank()) return offName;
        } catch (Throwable ignored) {
            // оффлайн-профиль не загрузился — покажем обрезок UUID
        }
        return id.toString().substring(0, 8);
    }

    public void setLastKnownName(Player p) {
        YamlConfiguration c = loadFile(p.getUniqueId());
        c.set("last-known-name", p.getName());
        save(p.getUniqueId());
    }
}
