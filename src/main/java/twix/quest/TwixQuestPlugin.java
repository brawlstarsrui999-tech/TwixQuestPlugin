package twix.quest;

import net.milkbowl.vault.economy.Economy;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import twix.quest.command.QuestCommand;
import twix.quest.command.QuestAdminCommand;
import twix.quest.data.PlayerQuestData;
import twix.quest.data.QuestRegistry;
import twix.quest.hook.BuyerHook;
import twix.quest.listener.MenuListener;
import twix.quest.listener.PlayerListener;
import twix.quest.manager.QuestManager;
import twix.quest.menu.MenuManager;
import twix.quest.util.TextUtil;

// Не final: тестовый фреймворк MockBukkit создаёт подкласс главного класса плагина.
public class TwixQuestPlugin extends JavaPlugin {

    private static TwixQuestPlugin instance;

    private QuestRegistry questRegistry;
    private PlayerQuestData playerData;
    private QuestManager questManager;
    private MenuManager menuManager;
    private BuyerHook buyerHook;
    private Economy vaultEconomy;
    private PlayerPointsAPI playerPointsAPI;

    @Override
    public void onEnable() {
        instance = this;

        TextUtil.init(this);

        this.questRegistry = new QuestRegistry(this);
        this.questRegistry.loadAll();
        this.playerData = new PlayerQuestData(this);
        this.questManager = new QuestManager(this, questRegistry, playerData);
        this.menuManager = new MenuManager(this);
        this.buyerHook = BuyerHook.create(this, questManager);

        getLogger().info("╔════════════════════════════════════════════════╗");
        getLogger().info("║     TwixQuestPlugin для TwixRPG запущен       ║");
        getLogger().info("║     Загружено квестов: " + String.format("%-4d                 ║", questRegistry.size()));
        getLogger().info("╚════════════════════════════════════════════════╝");

        Bukkit.getPluginManager().registerEvents(new PlayerListener(this, playerData, questManager), this);
        Bukkit.getPluginManager().registerEvents(new MenuListener(this), this);

        PluginCommand cmd = getCommand("quests");
        if (cmd != null) {
            QuestCommand handler = new QuestCommand(this, menuManager);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        PluginCommand adm = getCommand("tqadmin");
        if (adm != null) {
            QuestAdminCommand ah = new QuestAdminCommand(this, questManager, questRegistry, playerData);
            adm.setExecutor(ah);
            adm.setTabCompleter(ah);
        }

        setupVault();
        setupPlayerPoints();
        startTasks();
    }

    /**
     * Две общие задачи вместо слушателя движения и персональных таймеров:
     * <ul>
     *   <li>раз в 2 секунды — проверка состояний без собственного события
     *       (баланс, «уже стою в нужном мире», «стою внутри бастиона»);</li>
     *   <li>раз в 10 секунд — сохранение прогресса на диск.</li>
     * </ul>
     */
    private void startTasks() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    questManager.poll(p);
                } catch (RuntimeException ex) {
                    getLogger().warning("Ошибка проверки квеста у " + p.getName() + ": " + ex);
                }
            }
        }, 40L, 40L);
        Bukkit.getScheduler().runTaskTimer(this, () -> playerData.flushDirty(), 200L, 200L);
    }

    @Override
    public void onDisable() {
        if (buyerHook != null) {
            buyerHook.unhook();
        }
        if (playerData != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                playerData.save(p.getUniqueId());
            }
            playerData.flushDirty();
        }
        getLogger().info("TwixQuestPlugin выключен.");
    }

    /** Предупреждения об отсутствии Vault/PlayerPoints пишем один раз, а не при каждой проверке. */
    private boolean vaultWarned;
    private boolean pointsWarned;

    public void setupVault() {
        if (vaultEconomy != null) return;
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            if (!vaultWarned) getLogger().warning("Vault не найден — экономические награды будут недоступны.");
            vaultWarned = true;
            return;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            if (!vaultWarned) getLogger().warning("Провайдер экономики пока не найден — повторим поиск, когда он понадобится.");
            vaultWarned = true;
            return;
        }
        vaultEconomy = rsp.getProvider();
        getLogger().info("Подключено к экономике Vault: " + vaultEconomy.getName());
    }

    private void setupPlayerPoints() {
        if (playerPointsAPI != null) return;
        if (getServer().getPluginManager().getPlugin("PlayerPoints") == null) {
            if (!pointsWarned) getLogger().warning("PlayerPoints не найден — награды Twixcoin будут недоступны.");
            pointsWarned = true;
            return;
        }
        try {
            // В PlayerPoints 3.3.x экземпляр получается через singleton PlayerPoints#getInstance().
            org.black_ixx.playerpoints.PlayerPoints pp =
                    org.black_ixx.playerpoints.PlayerPoints.getInstance();
            if (pp == null) {
                pp = (org.black_ixx.playerpoints.PlayerPoints) getServer().getPluginManager().getPlugin("PlayerPoints");
            }
            if (pp != null) {
                playerPointsAPI = pp.getAPI();
                if (playerPointsAPI != null) {
                    getLogger().info("Подключено к PlayerPoints (Twixcoin).");
                } else if (!pointsWarned) {
                    getLogger().warning("PlayerPoints.getAPI() вернул null — награды Twixcoin будут недоступны.");
                }
            } else if (!pointsWarned) {
                getLogger().warning("Не удалось получить экземпляр PlayerPoints.");
            }
        } catch (Throwable t) {
            if (!pointsWarned) getLogger().warning("Не удалось подключиться к PlayerPoints: " + t.getMessage());
        }
        pointsWarned = true;
    }

    public void reloadAll() {
        // В плагине нет config.yml — все настройки в quests.yml.
        TextUtil.init(this);
        questRegistry.loadAll();
    }

    public static TwixQuestPlugin inst() { return instance; }

    public QuestRegistry getQuestRegistry() { return questRegistry; }
    public PlayerQuestData getPlayerData() { return playerData; }
    public QuestManager getQuestManager() { return questManager; }
    public MenuManager getMenuManager() { return menuManager; }
    /**
     * Экономика Vault. Если на момент запуска плагина провайдер ещё не был
     * зарегистрирован (он мог загрузиться позже), ищем его снова — иначе награды
     * монетами и квест на баланс навсегда остались бы нерабочими.
     */
    public Economy getVaultEconomy() {
        if (vaultEconomy == null) setupVault();
        return vaultEconomy;
    }

    public PlayerPointsAPI getPlayerPointsAPI() {
        if (playerPointsAPI == null) setupPlayerPoints();
        return playerPointsAPI;
    }

    /** Хук на плагин байера для квестов с продажей ему. */
    public BuyerHook getBuyerHook() { return buyerHook; }
}
