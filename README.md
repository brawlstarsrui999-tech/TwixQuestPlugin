# TwixQuestPlugin для TwixRPG 1.21.11

Плагин с фиолетовой тематикой, реализующий основное меню квестов с топом скорости,
деревом основных квестов и заготовкой для клановых.

## Особенности
- 35 готовых квестов из ТЗ TwixRPG, редактируемых через `plugins/TwixQuestPlugin/quests.yml`.
- Автоматическое отслеживание прогресса: следующий квест стартует только после прохождения предыдущего.
- Игроки видят только пройденные и текущий квест.
- Поздравление в чат: номер квеста и красиво оформленная награда.
- Интеграция с Vault (монеты), PlayerPoints (Twixcoin) и BaerPlugin (продажа байеру).
- Клановые квесты — заглушка (WIP) с пояснением в меню.
- Топ-3 самых быстрых игроков по времени прохождения всех квестов.

## Установка
1. Бросьте `TwixQuestPlugin-1.0.0.jar` в папку `plugins/`.
2. Установите зависимости:
   - `Paper 1.21.11`
   - `Vault` (https://www.spigotmc.org/resources/vault.34315/)
   - `PlayerPoints` (https://www.spigotmc.org/resources/playerpoints.80745/)
   - `BaerPlugin` (`brawlstarsrui999-tech/BaerPlugin`) — для квестов на продажу байеру.
3. Перезапустите сервер. В `plugins/TwixQuestPlugin/quests.yml` будут все квесты.

## Команды
- `/quests` (алиасы: `/quest`, `/twixquests`, `/tq`) — открыть меню квестов.
- `/tqadmin reload` — перезагрузка конфигурации.
- `/tqadmin reset <player>` — сброс прогресса игрока.
- `/tqadmin complete <player> <active|questId>` — завершить текущий/конкретный квест.
- `/tqadmin info <player>` — статистика игрока.

## Настройка квестов
Откройте `plugins/TwixQuestPlugin/quests.yml`. Поддерживаемые типы:

| Тип             | Описание                                              | Поля                                  |
|-----------------|-------------------------------------------------------|---------------------------------------|
| `MINE_BLOCK`     | Добыть N блоков                                       | `material`, `amount`                  |
| `CRAFT_ITEM`     | Скрафтить N предметов                                 | `material`, `amount`                  |
| `KILL_MOB`       | Убить N определённых мобов                            | `entity-type`, `amount`               |
| `KILL_PLAYER`    | Убить N игроков (с условием 30+ минут игры жертвы)    | `amount`                              |
| `ENTER_WORLD`    | Зайти в мир (по `world-env` или `world-name`)         | `world-env`/`world-name`, `amount`    |
| `VISIT_STRUCTURE`| Побывать рядом с данной структурой                    | `structure`, `structure-radius`       |
| `TRADE_VILLAGER` | Торгнуть с любым жителем                              | `amount`                              |
| `SELL_TO_BUYER`  | Продать что-то байеру                                 | `material`, `amount`                  |
| `REACH_BALANCE`  | Накопить N монет (Vault)                              | `amount`                              |
| `DEPOSIT_ITEM`   | Иметь N штук предмета в инвентаре                    | `material`, `amount`                  |
| `MOUNT_STRIDER`  | Оседлать лавомерку в аду                              | `amount`                              |

### Структура квеста
```yaml
2:
  name: "Скрафтите каменную кирку"
  description: "Скрафтите свою первую кирку."
  type: CRAFT_ITEM
  material: STONE_PICKAXE
  amount: 1
  reward:
    items:
      - material: COBBLESTONE
        amount: 25
        name: "<color:#9B59FF><b>Мешок булыжника</b></color>"
    vault-coins: 0
    twix-coins: 0
    exp-levels: 0
```

Цвета и форматирование используют [MiniMessage](https://docs.advntr.dev/minimessage/format.html):
поддерживаются HEX `#RRGGBB`, `<color:...>`, `<b>`, `<i>`, `<gradient:#..:#..>`, `<click:run_command:/foo>`, `<hover:...>`.

## Байер-интеграция
Плагин BaerPlugin — это покупатель для ресурсов. TwixQuestPlugin отслеживает команды вида
`/buyer sell`, `/baer sell`, `/bay sell` (предмет в основной руке). Также зарегистрирован
сервис `twix.quest.hook.BuyerHook$TwixQuestManagerBridge` в Bukkit `ServicesManager`,
который BaerPlugin может вызвать для максимально точной интеграции:

```java
import twix.quest.hook.BuyerHook.TwixQuestManagerBridge;
TwixQuestManagerBridge bridge = Bukkit.getServicesManager().load(TwixQuestManagerBridge.class);
if (bridge != null) bridge.notifySell(player, Material.COBBLESTONE, amount);
```

## Лицензия
См. конфигурацию репозитория.
