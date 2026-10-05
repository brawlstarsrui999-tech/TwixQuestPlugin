package twix.quest;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import twix.quest.quest.QuestDefinition;

/** ВРЕМЕННО: печатает меню текстом, чтобы проверить оформление глазами. */
class MenuDumpTest {
    private ServerMock server;
    private TwixQuestPlugin plugin;
    private PlayerMock player;

    @BeforeEach void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(TwixQuestPlugin.class);
        player = server.addPlayer("Steve");
    }
    @AfterEach void tearDown() { MockBukkit.unmock(); }

    private static String p(Component c) { return c == null ? "" : PlainTextComponentSerializer.plainText().serialize(c); }

    private void dump(String label, Inventory inv, boolean skipPanes) {
        System.out.println("DIAG ===== " + label + " (" + inv.getSize() + " слотов)");
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s == null) continue;
            if (skipPanes && s.getType() == Material.PURPLE_STAINED_GLASS_PANE) continue;
            ItemMeta m = s.getItemMeta();
            String name = m == null ? "" : p(m.displayName());
            System.out.println("DIAG [" + i + "] " + s.getType() + " x" + s.getAmount() + " | " + name);
            if (m != null && m.lore() != null) for (Component l : m.lore()) System.out.println("DIAG        " + p(l));
        }
    }

    @Test
    void dumpMenus() {
        // квесты 1-12 пройдены, активен 13 (два материала), часть вложена
        for (QuestDefinition q : plugin.getQuestRegistry().all()) {
            if (q.id >= 13) break;
            plugin.getQuestManager().completeQuest(player.getUniqueId(), q);
        }
        player.getInventory().clear();
        player.getInventory().addItem(new ItemStack(Material.ENDER_EYE, 5), new ItemStack(Material.BLAZE_ROD, 2));

        plugin.getMenuManager().openTree(player);
        Inventory tree = player.getOpenInventory().getTopInventory();
        System.out.println("DIAG заголовок дерева: " + p(player.getOpenInventory().title()));
        for (int slot : new int[]{4, 9, 20, 21, 22, 34}) {
            // только выбранные слоты: завершённый (9), активный (21), закрытые
            ItemStack s = tree.getItem(slot);
            if (s == null) continue;
            ItemMeta m = s.getItemMeta();
            System.out.println("DIAG дерево[" + slot + "] " + s.getType() + " | " + p(m.displayName()));
            if (m.lore() != null) for (Component l : m.lore()) System.out.println("DIAG        " + p(l));
        }

        for (int id : new int[]{13, 20, 24, 9, 31, 25}) {
            plugin.getMenuManager().openInfo(player, plugin.getQuestRegistry().byId(id));
            dump("ИНФО квеста #" + id + " «" + plugin.getQuestRegistry().byId(id).name + "»; заголовок: "
                    + p(player.getOpenInventory().title()), player.getOpenInventory().getTopInventory(), true);
        }
    }
}
