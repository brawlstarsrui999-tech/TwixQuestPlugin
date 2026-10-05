package twix.quest.reward;

import twix.quest.util.TextUtil;

import java.util.ArrayList;
import java.util.List;

/** Описывает награду за квест. */
public final class Reward {

    /** Предметы (с именами и чарами). */
    public final List<RewardItem> items;
    /** Количество монет Vault (если > 0). */
    public final int vaultCoins;
    /** Количество Twixcoin (PlayerPoints). */
    public final int twixCoins;
    /** Уровни опыта (если > 0). */
    public final int expLevels;
    /** Командные награды: выполняются от имени консоли, %player% заменяется на ник. */
    public final List<String> commands;
    /** Устаревшее поле (текст поздравления); оставлено для совместимости со старыми quests.yml. */
    public final String message;

    public Reward(List<RewardItem> items, int vaultCoins, int twixCoins, int expLevels, List<String> commands, String message) {
        this.items = items == null ? List.of() : items;
        this.vaultCoins = vaultCoins;
        this.twixCoins = twixCoins;
        this.expLevels = expLevels;
        this.commands = commands == null ? List.of() : commands;
        this.message = message;
    }

    public boolean isEmpty() {
        return items.isEmpty() && vaultCoins <= 0 && twixCoins <= 0 && expLevels <= 0 && commands.isEmpty();
    }

    /** Монеты Vault, Twixcoin и уровни опыта — то, что не является предметом. */
    public boolean hasCurrency() {
        return vaultCoins > 0 || twixCoins > 0 || expLevels > 0;
    }

    public String coinsText() {
        return "⛃ " + TextUtil.num(vaultCoins) + " " + TextUtil.plural(vaultCoins, "монета", "монеты", "монет");
    }

    public String twixText() {
        return "✦ " + TextUtil.num(twixCoins) + " Twixcoin";
    }

    public String expText() {
        return "✯ " + expLevels + " " + TextUtil.plural(expLevels, "уровень", "уровня", "уровней") + " опыта";
    }

    /** Многострочное описание для лора меню (MiniMessage-строки). */
    public List<String> lines() {
        List<String> out = new ArrayList<>();
        for (RewardItem i : items) {
            out.add(i.line());
            out.addAll(i.enchantLines());
        }
        if (vaultCoins > 0) out.add("<#F1C40F>▸ " + coinsText());
        if (twixCoins > 0) out.add("<#7FDBFF>▸ " + twixText());
        if (expLevels > 0) out.add("<#7CFC00>▸ " + expText());
        if (!commands.isEmpty()) out.add("<#BB8CFF>▸ <#E6E6FA>Особая награда");
        if (out.isEmpty()) out.add("<dark_gray>Без награды");
        return out;
    }

    /** Одной строкой — для сообщений в чате. */
    public String inline() {
        List<String> parts = new ArrayList<>();
        for (RewardItem i : items) {
            parts.add(i.label() + "<reset>" + (i.amount > 1 ? " <white>×" + i.amount : ""));
        }
        if (vaultCoins > 0) parts.add("<#F1C40F>" + coinsText());
        if (twixCoins > 0) parts.add("<#7FDBFF>" + twixText());
        if (expLevels > 0) parts.add("<#7CFC00>" + expText());
        if (!commands.isEmpty()) parts.add("<#E6E6FA>особая награда");
        if (parts.isEmpty()) return "<dark_gray>без награды";
        return String.join("<dark_gray>, ", parts);
    }
}
