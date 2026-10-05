package twix.quest;

import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import twix.quest.data.PlayerQuestData;
import twix.quest.manager.QuestManager;
import twix.quest.quest.QuestDefinition;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Интеграция с внешним миром: байер, экономика Vault, файлы на диске.
 * Байер имитируется ровно так, как он работает по исходникам BaerPlugin:
 * GUI с заголовком «§5§lБайер §8» §7Руда» (§-коды внутри обычного текста), продажа забирает
 * предметы из инвентаря и сразу начисляет деньги, затем окно закрывается/переоткрывается.
 */
class QuestIntegrationTest {

    private ServerMock server;
    private TwixQuestPlugin plugin;
    private PlayerMock player;
    private UUID id;
    private final Map<UUID, Double> balances = new HashMap<>();

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
        assertNotNull(q);
        return q;
    }

    private void completeBefore(int questId) {
        for (QuestDefinition q : plugin.getQuestRegistry().all()) {
            if (q.id >= questId) break;
            qm().completeQuest(id, q);
        }
        player.getInventory().clear();
        balances.clear();
    }

    private int count(Material m) {
        int n = 0;
        for (ItemStack s : player.getInventory().getContents()) {
            if (s != null && s.getType() == m) n += s.getAmount();
        }
        return n;
    }

    private static UUID uuidOf(Object o) {
        return o instanceof OfflinePlayer op ? op.getUniqueId() : UUID.nameUUIDFromBytes(String.valueOf(o).getBytes());
    }

    /** Экономика Vault на динамическом прокси: баланс хранится в {@link #balances}. */
    private Economy installEconomy() {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "getName": return "FakeEconomy";
                case "isEnabled": return true;
                case "hasAccount", "createPlayerAccount": return true;
                case "getBalance": return balances.getOrDefault(uuidOf(args[0]), 0.0);
                case "has": return balances.getOrDefault(uuidOf(args[0]), 0.0) >= (Double) args[args.length - 1];
                case "depositPlayer", "withdrawPlayer": {
                    double amount = (Double) args[args.length - 1];
                    double signed = method.getName().equals("depositPlayer") ? amount : -amount;
                    double now = balances.merge(uuidOf(args[0]), signed, Double::sum);
                    return new EconomyResponse(amount, now, EconomyResponse.ResponseType.SUCCESS, "");
                }
                default: {
                    Class<?> rt = method.getReturnType();
                    if (rt == boolean.class) return false;
                    if (rt == int.class) return 0;
                    if (rt == double.class) return 0.0;
                    return null;
                }
            }
        };
        Economy eco = (Economy) Proxy.newProxyInstance(Economy.class.getClassLoader(), new Class<?>[]{Economy.class}, handler);
        MockBukkit.createMockPlugin("Vault");
        server.getServicesManager().register(Economy.class, eco, plugin, ServicePriority.Normal);
        return eco;
    }

    /** Окно байера: заголовок — как у BuyerPlugin, с «§»-кодами внутри обычного текста. */
    private Inventory buyerGui() {
        return Bukkit.createInventory(null, 54, Component.text("§5§lБайер §8» §7Руда"));
    }

    /** «Продажа» так, как её делает BuyerPlugin: убрать предметы, начислить деньги, переоткрыть окно. */
    private void sell(Economy eco, Material m, int amount, double money) {
        player.getInventory().addItem(new ItemStack(m, amount));
        player.openInventory(buyerGui());
        player.getInventory().removeItem(new ItemStack(m, amount));
        if (eco != null) eco.depositPlayer(player, money);
        player.closeInventory();
    }

    // ---------------------------------------------------------------- байер

    @Test
    void sellingThroughBuyerGuiCompletesQuestAndPaysCoins() {
        Economy eco = installEconomy();
        completeBefore(3);

        sell(eco, Material.COBBLESTONE, 5, 5.0);

        assertTrue(plugin.getPlayerData().isCompleted(id, 3), "продажа любого предмета байеру должна закрыть квест #3");
        assertEquals(105.0, balances.get(id), 0.001, "5 монет за продажу + 100 за квест");
    }

    @Test
    void sellingWorksEvenWithoutAnyEconomyPlugin() {
        completeBefore(3);
        sell(null, Material.DIRT, 3, 0);
        assertTrue(plugin.getPlayerData().isCompleted(id, 3));
    }

    @Test
    void closingBuyerWithoutSellingDoesNotCount() {
        completeBefore(3);
        player.getInventory().addItem(new ItemStack(Material.COBBLESTONE, 10));
        player.openInventory(buyerGui());
        player.closeInventory();
        assertFalse(plugin.getPlayerData().isCompleted(id, 3));
    }

    @Test
    void removingItemsOutsideBuyerDoesNotCount() {
        completeBefore(3);
        player.getInventory().addItem(new ItemStack(Material.COBBLESTONE, 10));
        player.getInventory().removeItem(new ItemStack(Material.COBBLESTONE, 10)); // положил блоки
        server.getScheduler().performTicks(60);
        assertFalse(plugin.getPlayerData().isCompleted(id, 3));
    }

    @Test
    void buyingFromBuyerDoesNotCountAsSelling() {
        Economy eco = installEconomy();
        completeBefore(3);
        player.openInventory(buyerGui());
        player.getInventory().addItem(new ItemStack(Material.COBBLESTONE, 5)); // купил
        eco.withdrawPlayer(player, 10.0);
        player.closeInventory();
        assertFalse(plugin.getPlayerData().isCompleted(id, 3), "покупка не должна засчитываться как продажа");
    }

    @Test
    void sugarCaneQuestAccumulatesAcrossSalesAndPays() {
        Economy eco = installEconomy();
        completeBefore(24);

        sell(eco, Material.SUGAR_CANE, 100, 300.0);
        assertEquals(100, qm().currentProgress(id, quest(24)));
        sell(eco, Material.DIRT, 50, 10.0); // посторонний товар не считается
        assertEquals(100, qm().currentProgress(id, quest(24)));
        sell(eco, Material.SUGAR_CANE, 150, 450.0);

        assertTrue(plugin.getPlayerData().isCompleted(id, 24));
        assertEquals(300.0 + 10.0 + 450.0 + 1500.0, balances.get(id), 0.001);
    }

    @Test
    void staleCommandSessionDoesNotCountLaterInventoryChanges() throws Exception {
        completeBefore(3);
        player.getInventory().addItem(new ItemStack(Material.COBBLESTONE, 10));
        // команда «из списка байера», но GUI байера так и не открылось (чужой плагин / нет прав)
        server.getPluginManager().callEvent(new PlayerCommandPreprocessEvent(player, "/shop"));
        Thread.sleep(5300);
        player.getInventory().removeItem(new ItemStack(Material.COBBLESTONE, 10)); // игрок расставил блоки
        server.getScheduler().performTicks(40);
        assertFalse(plugin.getPlayerData().isCompleted(id, 3), "висящая сессия не должна засчитывать посторонние изменения");
    }

    // ---------------------------------------------------------------- монеты и баланс

    @Test
    void coinRewardsWorkEvenIfEconomyRegistersAfterOurPluginStarted() {
        // setUp уже прошёл без Vault; провайдер появляется позже — как при другом порядке загрузки плагинов
        Economy eco = installEconomy();
        assertNotNull(plugin.getVaultEconomy(), "экономику нужно искать повторно, а не один раз при старте");
        completeBefore(3);
        qm().completeQuest(id, quest(3));
        assertEquals(100.0, balances.get(id), 0.001);
        assertNotNull(eco);
    }

    @Test
    void balanceQuestCompletesWhenRichEnough() {
        installEconomy();
        completeBefore(21);

        balances.put(id, 3499.0);
        qm().poll(player);
        assertFalse(plugin.getPlayerData().isCompleted(id, 21));
        assertEquals(3499, qm().currentProgress(id, quest(21)), "в меню должен показываться реальный баланс");

        balances.put(id, 3500.0);
        qm().poll(player);
        assertTrue(plugin.getPlayerData().isCompleted(id, 21));
        assertEquals(4, count(Material.GOLD_BLOCK));
    }

    // ---------------------------------------------------------------- файлы

    @Test
    void outdatedQuestsFileOnServerIsReplacedAndBackedUp() throws Exception {
        File file = new File(plugin.getDataFolder(), "quests.yml");
        Files.writeString(file.toPath(), "config-version: 1\nquests:\n  1:\n    name: Старый\n    type: KILL_MOB\n    entity-type: ZOMBIE\n    amount: 1\n");

        plugin.getQuestRegistry().loadAll();

        assertEquals(35, plugin.getQuestRegistry().size(), "должен загрузиться новый файл");
        String[] backups = plugin.getDataFolder().list((dir, name) -> name.startsWith("quests.yml.bak-v1-"));
        assertNotNull(backups);
        assertEquals(1, backups.length, "старый файл должен быть сохранён рядом");
        assertTrue(Files.readString(new File(plugin.getDataFolder(), backups[0]).toPath()).contains("Старый"));
    }

    @Test
    void customQuestsFileWithNewerVersionIsKept() throws Exception {
        File file = new File(plugin.getDataFolder(), "quests.yml");
        Files.writeString(file.toPath(), "config-version: 999\nquests:\n  1:\n    name: Мой квест\n    type: KILL_MOB\n    entity-type: ZOMBIE\n    amount: 1\n");

        plugin.getQuestRegistry().loadAll();

        assertEquals(1, plugin.getQuestRegistry().size(), "файл администратора трогать нельзя");
        assertEquals("Мой квест", plugin.getQuestRegistry().byId(1).name);
        String[] backups = plugin.getDataFolder().list((dir, name) -> name.startsWith("quests.yml.bak"));
        assertTrue(backups == null || backups.length == 0);
    }

    @Test
    void progressIsReadBackFromDiskByAFreshDataStore() {
        completeBefore(22);
        player.getInventory().addItem(new ItemStack(Material.COBBLESTONE, 300));
        plugin.getMenuManager().openTree(player);
        player.simulateInventoryClick(player.getOpenInventory(), org.bukkit.event.inventory.ClickType.LEFT, 9 + 21);

        // Новый объект хранилища читает только файлы — как после рестарта сервера.
        PlayerQuestData reloaded = new PlayerQuestData(plugin);
        assertEquals(300, reloaded.getCounter(id, 22, "COBBLESTONE"));
        assertTrue(reloaded.isCompleted(id, 21));
        assertFalse(reloaded.isCompleted(id, 22));
    }

    @Test
    void adminResetWipesProgressToo() {
        completeBefore(22);
        player.getInventory().addItem(new ItemStack(Material.COBBLESTONE, 100));
        plugin.getMenuManager().openTree(player);
        player.simulateInventoryClick(player.getOpenInventory(), org.bukkit.event.inventory.ClickType.LEFT, 9 + 21);
        assertEquals(100, qm().deposited(id, quest(22), Material.COBBLESTONE));

        plugin.getPlayerData().reset(id);

        assertEquals(0, qm().deposited(id, quest(22), Material.COBBLESTONE));
        assertEquals(1, qm().currentQuest(id).id);
        assertNull(plugin.getPlayerData().loadFile(id).getConfigurationSection("progress"));
    }
}
