package twix.quest.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Хук для интеграции с {@code BuyerPlugin} (BaerPlugin by brawlstarsrui999-tech).
 *
 * <p>Использование: при продаже игроком предмета "покупателю" из байер-плагина
 * покупатель делает вызов API для начисления баланса на счёт игрока.
 * Универсального API в публичной части BaerPlugin мы не знаем, поэтому
 * предоставляем сервисный метод: {@link #onSell(Player, Material, int)}.
 *
 * <p>В квестах с типом {@link twix.quest.quest.QuestType#SELL_TO_BUYER} мы
 * регистрируем {@link ServiceManager} — и если байер-плагин использует тот же
 * service-токен, то получит в {@code plugin.yml} соответствующий contract
 * и сам вызывает наш метод.
 *
 * <p>Альтернативно: мониторим команды байера (как минимум
 * {@code /buyer sell}) через {@link #onCommandPreprocess(String, String[])}.
 */
public final class BuyerHook {

    private final JavaPlugin plugin;
    private final SellListener listener;

    private BuyerHook(JavaPlugin plugin, SellListener listener) {
        this.plugin = plugin;
        this.listener = listener;
    }

    public static BuyerHook create(JavaPlugin plugin) {
        return new BuyerHook(plugin, new SellListener() {
            @Override
            public void onSell(Player p, ItemStack stack, double rewardMoney) {
                TwixQuestManagerBridge bridge = plugin.getServer().getServicesManager().load(TwixQuestManagerBridge.class);
                if (bridge != null) bridge.notifySell(p, stack.getType(), stack.getAmount());
                else if (plugin instanceof TwixQuestSeller) {
                    ((TwixQuestSeller) plugin).handleSell(p, stack, stack.getAmount());
                }
            }
        });
    }

    public void notifySale(Player p, ItemStack stack, double amount) {
        if (stack == null) return;
        listener.onSell(p, stack, amount);
    }

    public interface SellListener {
        void onSell(Player p, ItemStack stack, double rewardMoney);
    }

    /** Контракт для байер-плагина, подписанного на Bukkit ServicesManager. */
    public interface TwixQuestManagerBridge {
        void notifySell(Player p, org.bukkit.Material material, int amount);
    }

    /** Чтобы наш плагин сам имел bridge, а байер смог найти его через ServicesManager. */
    public interface TwixQuestSeller {
        void handleSell(Player p, ItemStack stack, int amount);
    }

    /** Обёртка-утилита для удобной установки. */
    public void registerBridge(TwixQuestManagerBridge bridge) {
        Bukkit.getServicesManager().register(TwixQuestManagerBridge.class, bridge, plugin, org.bukkit.plugin.ServicePriority.Normal);
    }
}
