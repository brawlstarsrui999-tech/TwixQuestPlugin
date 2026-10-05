#!/bin/bash
# ВРЕМЕННО: мутационная проверка тестов. Ломаем код и убеждаемся, что тесты это замечают.
cd "$GITHUB_WORKSPACE" || exit 1
run_mut() {
  name="$1"; file="$2"; expr="$3"; tests="$4"
  cp "$file" /tmp/orig.bak
  sed -i "$expr" "$file"
  if cmp -s "$file" /tmp/orig.bak; then echo "MUT $name: МУТАЦИЯ НЕ ПРИМЕНИЛАСЬ"; return; fi
  mvn -B -ntp -q -Dtest="$tests" -Dsurefire.failIfNoSpecifiedTests=false test > /tmp/mut.log 2>&1
  code=$?
  cp /tmp/orig.bak "$file"
  if [ $code -ne 0 ]; then
    echo "MUT $name: ПОЙМАНА ($(grep -E '^\[ERROR\]   [A-Za-z]+Test\.' /tmp/mut.log | head -3 | sed 's/\[ERROR\]   //' | cut -c1-110 | tr '\n' ';'))"
  else
    echo "MUT $name: ВЫЖИЛА — тесты НЕ заметили поломку!"
  fi
}
B=src/main/java/twix/quest
run_mut "M1 сессия байера без истечения"   $B/hook/BuyerHook.java            's/GUI_CONFIRM_MS = 5_000L/GUI_CONFIRM_MS = 5_000_000L/'                                   QuestIntegrationTest
run_mut "M2 чары снова теряются"            $B/data/RewardBuilder.java         's/Map<String, Object> enchMap = asMap(m.get("enchantments"));/Map<String, Object> enchMap = null;/' 'QuestsConfigTest,QuestFlowTest'
run_mut "M3 все слоты ведут на квест #1"    $B/menu/MenuManager.java           's/st.slotQuest.put(slot, q.id);/st.slotQuest.put(slot, 1);/'                              QuestFlowTest
run_mut "M4 вложение забирает всё подряд"   $B/manager/QuestManager.java       's/int need = r.amount - have;/int need = 1000000;/'                                       QuestFlowTest
run_mut "M5 забирает и именные предметы"    $B/manager/QuestManager.java       's/if (plainOnly \&\& !s.isSimilar(proto)) continue;/\/\/ mutated/'                          QuestFlowTest
run_mut "M6 экономика ищется один раз"      $B/TwixQuestPlugin.java            's/if (vaultEconomy == null) setupVault();/\/\/ mutated/'                                  QuestIntegrationTest
run_mut "M7 у всех квестов иконка-дуб"      $B/quest/QuestDefinition.java      's/if (iconOverride != null) return iconOverride;/return Material.OAK_LOG;/'               QuestFlowTest
echo "MUT --- готово"
