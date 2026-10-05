package twix.quest.data;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import twix.quest.reward.Reward;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** ВРЕМЕННАЯ диагностика старого поведения (печатает строки DIAG). */
class DiagnosticsTest {

    @Test
    void oldRewardParsing() throws Exception {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(
                new InputStreamReader(getClass().getResourceAsStream("/quests.yml"), StandardCharsets.UTF_8));
        for (String id : new String[]{"1", "4", "7", "11", "22", "25"}) {
            ConfigurationSection rs = yml.getConfigurationSection("quests." + id + ".reward");
            Object first = rs.getList("items").get(0);
            Object ench = ((java.util.Map<?, ?>) first).get("enchantments");
            try {
                Reward r = RewardBuilder.from(rs, null);
                System.out.println("DIAG reward q" + id + ": в yml enchantments=" + ench
                        + " (" + (ench == null ? "null" : ench.getClass().getSimpleName()) + ")"
                        + " -> у собранной награды чар: " + r.items.get(0).enchantments.size());
            } catch (Throwable t) {
                System.out.println("DIAG reward q" + id + ": исключение " + t);
            }
        }
    }

    @Test
    void oldTreeLookup() {
        MiniMessage mm = MiniMessage.miniMessage();
        StringBuilder wrong = new StringBuilder();
        int okCount = 0;
        for (int id = 1; id <= 35; id++) {
            Component name = mm.deserialize("<#D3A8FF><bold>● <#9B59FF>Квест #" + id
                    + " <dark_gray>· <#D3A8FF>Название").decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
            String plain = mm.serialize(name);
            int resolved = -1;
            for (int q = 1; q <= 35; q++) {
                if (plain.contains("#" + q + " ") || plain.contains("#" + q + " <") || plain.contains("Квест #" + q)) {
                    resolved = q;
                    break;
                }
            }
            if (resolved == id) okCount++;
            else wrong.append(id).append("->").append(resolved).append(' ');
            if (id == 5 || id == 12) System.out.println("DIAG serialized(" + id + ")=" + plain);
        }
        System.out.println("DIAG tree lookup: верно " + okCount + "/35; ошибки (активный->найденный): " + wrong);

        // сколько квестов вообще помещалось в дерево
        int quests = 35;
        int slots = Math.min(54, Math.max(27, ((quests + 8) / 9 + 1) * 9));
        int usable = 0;
        for (int i = 0; i < slots; i++) {
            int row = i / 9, col = i % 9;
            if (row == 0 || row == slots / 9 - 1 || col == 0 || col == 8) continue;
            usable++;
        }
        System.out.println("DIAG tree capacity: инвентарь " + slots + " слотов, под квесты " + usable + " из " + quests);
    }
}
