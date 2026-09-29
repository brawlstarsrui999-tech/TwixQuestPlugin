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
- Топ игроков: 9 мест (медали за 1–3), сортировка по числу пройденных квестов,
  при равенстве — по времени. Отдельно показывается ваше собственное место.

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
| `SELL_TO_BUYER`  | Продать что-то байеру (правый клик в `/buyer`)        | `material`*, `amount`, `count-buys`  |
| `BUY_FROM_BUYER` | Купить что-то у байера (левый клик в `/buyer`)        | `material`*, `amount`, `count-buys`  |
| `REACH_BALANCE`  | Накопить N монет (Vault)                              | `amount`                              |
| `DEPOSIT_ITEM`   | Иметь N штук предмета в инвентаре                    | `material`, `amount`                  |
| `MOUNT_STRIDER`  | Оседлать лавомерку в аду                              | `amount`                              |

\* Если `material` не указан, засчитывается ЛЮБОЙ предмет сделки — удобно для
квестов вида «продайте что-нибудь байеру».

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
Плагин байера (`brawlstarsrui999-tech/BaerPlugin`, имя плагина в его plugin.yml —
`BuyerPlugin`) **менять не нужно** — TwixQuestPlugin работает поверх него.

Как устроен сам байер (проверено по его исходникам, `com.buyerplugin.ShopGUI`):

- `/buyer` (алиасы `shop`, `магазин`) открывает GUI на 54 слота с заголовком
  `Байер » <категория>`;
- **продажи через команду нет**: `/buyer sell` не существует. Продажа — это
  **правый клик** по товару, покупка — **левый** (shift — по 64 штуки);
- после каждой сделки байер переоткрывает своё окно (`refreshLater`), то есть
  Bukkit шлёт `InventoryCloseEvent` по старому окну и `InventoryOpenEvent`
  по новому.

Что делает TwixQuestPlugin:

1. отмечает «сессию байера» — по команде `/buyer` **или** по заголовку GUI
   (ключевые слова настраиваются в `settings` в `quests.yml`);
2. запоминает снимок инвентаря и баланс игрока;
3. на каждом закрытии окна (включая автообновление байером) и раз в секунду
   сравнивает снимок с текущим инвентарём: чего стало меньше — продано,
   чего больше — куплено, и передаёт это в `QuestManager.onBuyerTrade`;
4. баланс Vault используется только как подсказка «продажа это или покупка»,
   поэтому квесты считаются и без подключённой экономики.

Прогресс приходит в текущий квест: для `SELL_TO_BUYER` — по проданным предметам,
для `BUY_FROM_BUYER` — по купленным. Флаг `count-buys: true` заставляет квест
засчитывать и встречную операцию. Если в квесте не указан `material`, считается
любой предмет сделки.

Настройка распознавания байера (`plugins/TwixQuestPlugin/quests.yml`):

```yaml
settings:
  buyer-gui-keywords: ["байер", "баер", "buyer", "baer"]
  buyer-command-labels: ["buyer", "baer", "shop", "магазин"]
```

Если у вас стоит своя сборка байера с другим заголовком/командой — просто
допишите туда нужные слова, перезапуск не требуется (`/tqadmin reload`).

Для своих байер-плагинов остался и прямой мост через Bukkit `ServicesManager`:

```java
import twix.quest.hook.BuyerHook.TwixQuestManagerBridge;
TwixQuestManagerBridge bridge = Bukkit.getServicesManager().load(TwixQuestManagerBridge.class);
if (bridge != null) bridge.notifySell(player, Material.COBBLESTONE, amount);
```

## Оформление текста
Все строки идут через MiniMessage. Цвета — **незакрываемые** теги: пишите
`<#9B59FF>` или `<gold>` и НЕ пишите `</gold>`/`</#9B59FF>`. Такой закрывающий
тег MiniMessage не отбрасывает, а выводит обычным текстом — именно так в топе
появлялись строки вида `<##F1C40F>Медаль #1`. На всякий случай `TextUtil`
теперь сам чинит обе опечатки (двойная решётка и закрывающий цветовой тег)
и пишет предупреждение в консоль, а при совсем битой строке показывает текст
без разметки вместо падения меню.

## Лицензия
См. конфигурацию репозитория.
