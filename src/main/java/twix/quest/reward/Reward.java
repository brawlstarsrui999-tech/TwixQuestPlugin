package twix.quest.reward;

import java.util.List;

/** Описывает награду за квест. */
public final class Reward {

    /** Сырые предметы (для дропа в инвентарь) с возможными энчантами. */
    public final List<RewardItem> items;
    /** Количество монет Vault (если > 0). */
    public final int vaultCoins;
    /** Количество Twixcoin (PlayerPoints) — для будущих квестов. */
    public final int twixCoins;
    /** Уровни опыта (если > 0). */
    public final int expLevels;
    /** Командные награды: выполняются строкой от CommandSender (player). */
    public final List<String> commands;
    /** Текст поздравления. */
    public final String message;

    public Reward(List<RewardItem> items, int vaultCoins, int twixCoins, int expLevels, List<String> commands, String message) {
        this.items = items;
        this.vaultCoins = vaultCoins;
        this.twixCoins = twixCoins;
        this.expLevels = expLevels;
        this.commands = commands;
        this.message = message;
    }

    public boolean isEmpty() {
        return (items == null || items.isEmpty())
                && vaultCoins <= 0 && twixCoins <= 0 && expLevels <= 0
                && (commands == null || commands.isEmpty());
    }

    /** Вывод в человеческом виде. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        if (items != null) {
            for (RewardItem i : items) {
                sb.append(i.pretty()).append(", ");
            }
        }
        if (vaultCoins > 0) sb.append("⛃ ").append(vaultCoins).append(" монет, ");
        if (twixCoins > 0)  sb.append("✦ ").append(twixCoins).append(" Twixcoin, ");
        if (expLevels > 0)  sb.append("✯ ").append(expLevels).append(" уровней опыта, ");
        if (commands != null) {
            for (String c : commands) sb.append("<dark_gray>(команда)<reset> ");
        }
        String s = sb.toString().trim();
        if (s.endsWith(",")) s = s.substring(0, s.length() - 1);
        return s.isEmpty() ? "<dark_gray>— без награды —" : s;
    }
}
