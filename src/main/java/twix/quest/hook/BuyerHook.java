package twix.quest.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;
import twix.quest.manager.QuestManager;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Хук для интеграции с {@code BaerPlugin} (brawlstarsrui999-tech/BaerPlugin, версия 3.1+).
 *
 * <p>BaerPlugin регистрирует свой API через Bukkit {@link ServicesManager} под
 * именем класса {@code com.buyerplugin.api.BuyerService}. Чтобы TwixQuestPlugin
 * оставался компилируемым без зависимости на BaerPlugin, мы резолвим API
 * через reflection и подписываемся на события транзацкий динамически.</p>
 *
 * <p>Если BaerPlugin не установлен или его версия ниже 3.1 — хук тихо
 * отключается, оставляя квесты SELL_TO_BUYER недостижимыми, но плагин
 * продолжает работать.</p>
 */
public final class BuyerHook {

    private final JavaPlugin plugin;
    private final QuestManager manager;
    private final AtomicReference<UUID> subscriptionId = new AtomicReference<>();
    private Object service;        // com.buyerplugin.api.BuyerService (через reflection)
    private Class<?> serviceClass; // com.buyerplugin.api.BuyerService

    private BuyerHook(JavaPlugin plugin, QuestManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    public static BuyerHook create(JavaPlugin plugin, QuestManager manager) {
        return new BuyerHook(plugin, manager);
    }

    /**
     * Попробовать подключиться к BaerPlugin через ServicesManager.
     * Безопасно вызывать когда BaerPlugin может быть ещё не загружен —
     * метод просто тихо завершится.
     */
    public void hook() {
        Plugin bp = Bukkit.getPluginManager().getPlugin("BuyerPlugin");
        if (bp == null) {
            plugin.getLogger().info("BaerPlugin (BuyerPlugin) не найден — квесты SELL_TO_BUYER будут недостижимы.");
            return;
        }
        try {
            serviceClass = Class.forName("com.buyerplugin.api.BuyerService");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning(
                    "BuyerPlugin установлен, но не публикует com.buyerplugin.api.BuyerService. " +
                    "Обновите BaerPlugin до версии 3.1+ для интеграции с SELL_TO_BUYER.");
            return;
        }
        ServicesManager sm = Bukkit.getServicesManager();
        // sm.getRegistration(Class<T>) стирает дженерик до Object, поэтому
        // мы перебираем все известные сервисы и ищем тот, чей провайдер
        // имплементит com.buyerplugin.api.BuyerService.
        for (Class<?> known : sm.getKnownServices()) {
            if (!serviceClass.isAssignableFrom(known)) continue;
            Object provider = sm.load(known);
            if (provider != null && serviceClass.isInstance(provider)) {
                service = provider;
                registerListener();
                plugin.getLogger().info("Подключились к BuyerService: " + service.getClass().getName());
                return;
            }
        }
        // Попробуем fallback: поискать среди ВСЕХ зарегистрированных провайдеров
        // (getKnownServices() иногда возвращает неполный список в старых Bukkit).
        try {
            Method getReg = sm.getClass().getMethod("getRegistrations", Plugin.class);
            for (Object rsp : (java.util.List<?>) getReg.invoke(sm, bp)) {
                Method getProvider = rsp.getClass().getMethod("getProvider");
                Object provider = getProvider.invoke(rsp);
                if (provider != null && serviceClass.isInstance(provider)) {
                    service = provider;
                    registerListener();
                    plugin.getLogger().info("Подключились к BuyerService (fallback): " + service.getClass().getName());
                    return;
                }
            }
        } catch (Throwable ignored) {
            // ignore
        }
        plugin.getLogger().warning("BuyerService не зарегистрирован. Квесты SELL_TO_BUYER не будут засчитываться.");
    }

    /**
     * Динамически создаёт прокси {@code BuyerListener.onTransaction(BuyerTransactionEvent)}
     * и регистрирует его через {@code BuyerService.register(listener)}.
     */
    private void registerListener() {
        try {
            Class<?> listenerClass = Class.forName("com.buyerplugin.api.BuyerListener");
            Class<?> eventClass    = Class.forName("com.buyerplugin.api.BuyerTransactionEvent");
            Class<?> typeClass     = Class.forName("com.buyerplugin.api.BuyerTransactionType");
            // SELL-константа enum'а: используем unchecked cast (Class<Enum>).
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object sellType = Enum.valueOf((Class<Enum>) typeClass, "SELL");

            Method getType    = eventClass.getMethod("getType");
            Method getPlayer  = eventClass.getMethod("getPlayer");
            Method getMaterial = eventClass.getMethod("getMaterial");
            Method getAmount  = eventClass.getMethod("getAmount");

            InvocationHandler handler = (proxy, method, args) -> {
                if (method.getName().equals("onTransaction") && args != null && args.length == 1) {
                    Object event = args[0];
                    try {
                        if (getType.invoke(event) == sellType) {
                            Player player = (Player) getPlayer.invoke(event);
                            if (player != null) {
                                org.bukkit.Material mat = (org.bukkit.Material) getMaterial.invoke(event);
                                Integer amount = (Integer) getAmount.invoke(event);
                                if (mat != null && amount != null && amount > 0) {
                                    ItemStack fake = new ItemStack(mat, amount);
                                    try {
                                        manager.onSellToBuyer(player, fake);
                                    } catch (Throwable t) {
                                        plugin.getLogger().warning("onSellToBuyer failed: " + t.getMessage());
                                    }
                                }
                            }
                        }
                    } catch (Throwable t) {
                        plugin.getLogger().warning("BuyerListener proxy error: " + t.getMessage());
                    }
                }
                return null;
            };
            Object proxy = Proxy.newProxyInstance(
                    plugin.getClass().getClassLoader(),
                    new Class<?>[] { listenerClass },
                    handler);

            // BuyerService.register(BuyerListener) → UUID
            Method registerMethod = serviceClass.getMethod("register", listenerClass);
            Object returned = registerMethod.invoke(service, proxy);
            if (returned instanceof UUID) {
                subscriptionId.set((UUID) returned);
            }
        } catch (Throwable t) {
            plugin.getLogger().severe("Не удалось зарегистрировать BuyerListener: " + t);
            t.printStackTrace();
        }
    }

    public void unhook() {
        if (service == null) return;
        UUID id = subscriptionId.get();
        if (id == null) return;
        try {
            Method unregister = serviceClass.getMethod("unregister", UUID.class);
            unregister.invoke(service, id);
        } catch (Throwable t) {
            plugin.getLogger().warning("Не удалось снять подписку BuyerListener: " + t.getMessage());
        } finally {
            subscriptionId.set(null);
            service = null;
        }
    }

    /**
     * Совместимость со старым мостом — если по какой-то причине сторонний
     * плагин зовёт нас через TwixQuestSeller, оставлено для обратной совместимости.
     */
    public void notifySale(Player p, ItemStack stack, double amount) {
        if (stack == null) return;
        manager.onSellToBuyer(p, stack);
    }

    /**
     * Регистрация моста в Bukkit ServicesManager — обратная совместимость
     * со старыми кастомными BuyerPlugin, использующими TwixQuestManagerBridge.
     */
    public void registerBridge(TwixQuestManagerBridge bridge) {
        Bukkit.getServicesManager().register(TwixQuestManagerBridge.class, bridge, plugin,
                org.bukkit.plugin.ServicePriority.Normal);
    }

    public interface SellListener {
        void onSell(Player p, ItemStack stack, double amount);
    }

    public interface TwixQuestManagerBridge {
        void notifySell(Player p, org.bukkit.Material material, int amount);
    }

    public interface TwixQuestSeller {
        void handleSell(Player p, ItemStack stack, int amount);
    }
}
