package twix.quest.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

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

    /** Топ N самых быстрых по последнему завершённому квесту в общей сложности. */
    public record SpeedEntry(UUID uuid, String name, long durationMs) {}
    public List<SpeedEntry> getSpeedTop(QuestRegistry registry, int limit) {
        Map<UUID, Long> finished = new HashMap<>();
        Map<UUID, String> names = new HashMap<>();
        File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) files = new File[0];
        for (File f : files) {
            try {
                String s = f.getName().substring(0, f.getName().length() - 4);
                UUID id = UUID.fromString(s);
                YamlConfiguration c = YamlConfiguration.loadConfiguration(f);
                long started = c.getLong("started", 0L);
                if (started == 0L) continue;
                ConfigurationSection cs = c.getConfigurationSection("completed");
                if (cs == null) continue;
                long latest = 0L;
                int done = 0;
                for (String k : cs.getKeys(false)) {
                    long ts = cs.getLong(k);
                    if (ts > latest) latest = ts;
                    done++;
                }
                int total = registry.size();
                if (done >= total) {
                    finished.put(id, latest - started);
                    names.put(id, c.getString("last-known-name", s.substring(0, 8)));
                }
            } catch (Exception ignored) {}
        }
        List<Map.Entry<UUID, Long>> entries = new ArrayList<>(finished.entrySet());
        entries.sort(Map.Entry.comparingByValue());
        List<SpeedEntry> top = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, entries.size()); i++) {
            Map.Entry<UUID, Long> e = entries.get(i);
            top.add(new SpeedEntry(e.getKey(), names.getOrDefault(e.getKey(), "?"), e.getValue()));
        }
        return top;
    }

    public void setLastKnownName(Player p) {
        YamlConfiguration c = loadFile(p.getUniqueId());
        c.set("last-known-name", p.getName());
        save(p.getUniqueId());
    }
}
