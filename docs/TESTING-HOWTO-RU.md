# Как устроены тесты в Settled Lands

Документ объясняет, как именно сделаны проверки в этом проекте, чтобы новые тесты
писались в том же стиле. Его можно целиком передать другому ИИ или разработчику.

Всего в проекте **36 unit-тестов** и **117 интеграционных проверок** на настоящем
выделенном сервере (119 при повторном запуске на том же мире). Ниже — обе части, приёмы и правила.

---

## 1. Зачем два уровня тестов

| Уровень | Что проверяет | Где лежит | Чем запускается |
|---|---|---|---|
| Unit (JUnit 5) | Чистая логика и числа: координаты ячеек, формулы прогресса, фазы ритуала, интерполяция анимации, правила горения | `src/test/java/dev/settledlands/` | `./gradlew build` (задача `:test`) |
| Интеграционные проверки | Реальная игра: блоки, лут, меню, команды, сущности, молния, NBT, спавн | `src/smoke/java/dev/settledlands/smoke/` | `./gradlew runServer -PsanctitySmoke` |

Логика, которую можно посчитать без Minecraft (числа, тайминги, формулы), выносится
в отдельные классы без игровых типов — например `Progress`, `Cell`, `RitualRules`,
`SanctityFire` — и покрывается unit-тестами. Всё, что зависит от поведения игры,
проверяется на живом сервере, потому что мок-сервер почти всегда врёт.

---

## 2. Unit-тесты

```java
class SanctityFireTest {
    @Test void onlySecondLevelBurns() {
        assertFalse(SanctityFire.burns(1));
        assertTrue(SanctityFire.burns(2));
    }
}
```

Правила:

- Имя метода описывает поведение, а не метод: `preservesFloatingHead`, `idleLoopsEachSecond`.
- Тестируются границы: `tick 139` — ещё нет, `tick 140` — уже да; сила 15/20/30.
- Если правило сложное, оно выносится в чистый класс именно ради теста.
- Параметризовать не обязательно, но перебор диапазонов приветствуется:
  например, проверка, что все интерполированные значения конечны на всём отрезке.

---

## 3. Интеграционные проверки: обвязка

### 3.1. Отдельный source set

В `build.gradle` набор включается свойством `-PsanctitySmoke`:

```groovy
if (providers.gradleProperty('sanctitySmoke').isPresent()) {
    sourceSets { smoke { compileClasspath += sourceSets.main.output; runtimeClasspath += sourceSets.main.output } }
    neoForge.addModdingDependenciesTo(sourceSets.smoke)
    neoForge.mods { "${mod_id}" { sourceSet sourceSets.smoke } }
    neoForge.runs.server { gameDirectory = file('run-smoke') }
    def smokeResult = file('run-smoke/sanctity-smoke-result.txt')
    tasks.named('runServer') {
        doFirst { smokeResult.delete() }
        doLast {
            if (!smokeResult.isFile() || !smokeResult.text.startsWith('PASSED')) {
                throw new GradleException('Sanctity integration suite failed; inspect run-smoke/logs/latest.log')
            }
        }
    }
}
```

Ключевые детали:

- Тестовый код **не попадает в релизный JAR** (source set отдельный; при упаковке
  это ещё и проверяется).
- Мир тестов — своя папка `run-smoke/`, поэтому прогон не трогает игровой мир.
- **Маркер `PASSED` обязателен.** Без него Gradle завершается с кодом 0, даже когда
  проверка упала и в логе написано «FAILED». Это самая частая ловушка.
- Перед первым прогоном в `run-smoke/` нужен `server.properties`
  (шаблон — `smoke-server/server.properties`: плоский мир, `view-distance=2`,
  `online-mode=false`).

### 3.2. Точка входа

`SanctitySmoke` подписан на `ServerStartedEvent`, выполняет проверки в потоке сервера,
печатает результат в лог, пишет маркер и выключает сервер:

```java
@EventBusSubscriber(modid=SettledLands.ID)
public final class SanctitySmoke {
    private static int checks;
    private static void check(boolean value,String message) {
        if(!value)throw new AssertionError(message);
        System.out.println("SANCTITY CHECK OK: "+message);checks++;
    }
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        MinecraftServer server=event.getServer();
        server.execute(()->{
            try { run(server);
                  checks+=RitualSmoke.run(server);
                  checks+=FireSmoke.run(server);
                  checks+=TableSmoke.run(server);
                  System.out.println("SANCTITY SMOKE PASSED: "+checks+" checks");
                  result("PASSED "+checks); }
            catch(Throwable t) { System.out.println("SANCTITY SMOKE FAILED");t.printStackTrace();result("FAILED "+t); }
            finally { server.halt(false); }
        });
    }
}
```

Каждый набор — обычный класс с методом `run(server)`, который возвращает число проверок
и печатает строки `CHECK OK: <текст>`. Текст пишется так, чтобы по нему одному было
понятно, что именно сломалось.

---

## 4. Приёмы внутри проверок

### 4.1. Никаких моков — только настоящие механики

Проверяется то, чем играет игрок:

| Что нужно | Чем делать |
|---|---|
| Поставить/сломать блок | `level.setBlockAndUpdate(...)`, `level.destroyBlock(pos,true,fakePlayer)` |
| Использовать предмет | `stack.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(...)))` |
| Лут блока | `Block.getDrops(state,level,pos,blockEntity)` — тот же путь, что в игре |
| Стол зачарования | реальный `EnchantmentMenu` + реальные книжные полки + `menu.clickMenuButton(player,row)` |
| Наковальня | реальный блок `Blocks.ANVIL` в мире + `AnvilMenu` + `anvil.createResult()` |
| Команда | настоящий диспетчер: `dispatcher.parse(cmd,source)` и `dispatcher.execute(...)` |
| Молния | реальный `LightningBolt`, которому вручную прокручиваются тики |
| Сохранение | `banner.saveWithFullMetadata(registries)` → `BlockEntity.loadStatic(...)` |

### 4.2. Фейковый игрок

```java
var player=FakePlayerFactory.getMinecraft(level);
player.setGameMode(GameType.SURVIVAL);
player.setPos(x+0.5,y+1,z+0.5);
```

- Меню проверяют расстояние до игрока (`stillValid`), поэтому фейкового игрока нужно
  **ставить рядом с блоком**, иначе меню «не откроется» и проверка упадёт по ложной причине.
- Командам нужны права: `player.createCommandSourceStack().withPermission(4)`.
- Каждому меню даётся **уникальный id** (`new EnchantmentMenu(151,...)`, `160`, `161`…),
  иначе несколько меню конфликтуют.
- Хитрость: `ItemStack` — `final`, поэтому «анонимный подкласс с блоком инициализации»
  внутри теста не компилируется. Зачаровывать надо отдельной строкой.

### 4.3. Управление временем и тиками

- Долгие интервалы (10 с, 20 с, 7 с ритуала) **не ждут**: время сдвигается
  `server.getWorldData().overworldData().setGameTime(...)`, а обработчик вызывается вручную
  (`new RitualManager().tick(new ServerTickEvent.Post(...))`).
- Время суток фиксируется ночью `level.setDayTime(18000)` — иначе зомби горит от солнца
  и проверка горения ловит ложный результат. Погода тоже глушится
  `level.setWeatherParameters(6000,0,false,false)`.
- Сущности прокручиваются вручную: `for(int i=0;i<20&&!bolt.isRemoved();i++)bolt.tick();`

### 4.4. Управление сценой

- Мир плоский, координаты проверок разнесены далеко (например 40/56/96/160 по X),
  чтобы наборы не мешали друг другу.
- Перед сценарием мусор убирается:
  `for(var e:level.getEntitiesOfClass(ItemEntity.class,box))e.discard();`
- Мобы создаются так: `EntityType.ZOMBIE.create(level)` → `setPos` → `setOnGround(true)`
  → `addFreshEntity`. Без `setOnGround` условия AI не выполняются.
- Если проверка требует работающего AI, моб должен быть **не** `setNoAi(true)`.

### 4.5. Измерение вместо утверждений

Перед тем как что-то утверждать, полезно **напечатать факт**. В `TableSmoke` есть
измеритель, который открывает стол с 5, 10 и 15 полками и по каждой строке печатает
цену, подсказку и результат клика, а также пишет отчёт в `run-smoke/table-report.txt`.

Именно так нашлась ошибка 0.4.2–0.4.3: «Святость I» не предлагалась, потому что её порог
силы был выше силы нижних строк стола. На глаз это выглядело как «в столе только старшие
уровни» и легко объяснялось неверными догадками (старый JAR, датапак), а замер показал
истинную причину за один прогон.

Правило: **сначала измерь, потом утверждай**. Каждая найденная ошибка превращается
в отдельную проверку с конкретными числами:

```java
int[] fullTop=clickRow(level,table,15,2,40);
check(fullTop[2]>0&&fullTop[1]==0,"the top row of a full table gives level II only: "+Arrays.toString(fullTop));
```

---

## 5. Как проверять поведение ванильных классов, не угадывая

Бинарник игры лежит в `build/moddev/artifacts/neoforge-21.1.252.jar`. Смотреть его
надо через `javap`, а не декомпилировать (декомпиляция падает по памяти):

```bash
javap -p -classpath build/moddev/artifacts/neoforge-21.1.252.jar net.minecraft.world.inventory.AnvilMenu
javap -p -c -classpath build/moddev/artifacts/neoforge-21.1.252.jar net.minecraft.world.item.enchantment.Enchantment > /tmp/ench.txt
```

Так были выяснены, например, три факта, которые невозможно угадать:

1. наковальня сливает чары только у двух одинаковых **повреждаемых** предметов или с книгой,
   поэтому два баннера она не соединяет в принципе;
2. `Enchantment.getFullname` дописывает римское числительное всегда, если `max_level > 1`,
   и только для одноуровневых чар скрывает единицу;
3. сила строки стола считается от enchantability предмета, случайного разброса и силы
   зачарования (power) по формуле вида `max(1, (enchantability/2 + 1 + random) * power / 3)`;
   измеренный разброс: при 5 полках строки имеют силу примерно 2–10, при 10 — 4–20,
   при 15 — 3–30 (точные числа прогона лежат в `run-smoke/table-report.txt`).

Порядок работы: `javap` → гипотеза → проверка на сервере → если поведение не совпало,
числовое измерение вместо спора.

---

## 6. Гигиена: что обязательно соблюдать

1. **Маркер результата.** Без `run-smoke/sanctity-smoke-result.txt` с `PASSED` сборка
   может «пройти», ничего не проверив.
2. **Детерминизм.** Никаких `Thread.sleep`, случайных координат и зависимости от времени
   суток. Всё время и погода задаются явно.
3. **Изоляция.** Каждая проверка убирает за собой сущности и мобы; координаты сцен
   разнесены; не размещать блоки на позиции важных объектов (одна ложная ошибка возникла
   именно из-за того, что тест замостил досками позицию баннера).
4. **Только реальные пути.** Нельзя «подготовить состояние» в обход игры и потом
   утверждать, что игровая механика работает.
5. **Тестов меньше, но конкретнее.** Одна проверка = одно утверждение с понятным текстом.
6. **Тестовый код не попадает в релиз** — отдельный source set и проверка при упаковке.
7. **Слабые машины:** `CI=true`, JDK 21, не экспортировать `_JAVA_OPTIONS` с большим
   `-Xmx` (дочерние JVM шагов сборки падают с «Failed to execute tool»), `chmod +x gradlew`
   после распаковки архива, `org.gradle.workers.max=2`.

---

## 7. Как добавить новый набор проверок

1. Создайте класс в `src/smoke/java/dev/settledlands/smoke/`, например `MySmoke.java`:

```java
public final class MySmoke {
    private static int checks;
    private static void check(boolean value,String message) {
        if(!value)throw new AssertionError(message);
        System.out.println("MY CHECK OK: "+message);checks++;
    }
    public static int run(MinecraftServer server) {
        ServerLevel level=server.overworld();
        BlockPos pos=new BlockPos(200,120,200);
        level.getChunkAt(pos);
        // ... реальные действия ...
        check(true,"описание того, что проверено");
        System.out.println("MY SMOKE PASSED: "+checks+" checks");return checks;
    }
}
```

2. Вызовите его из `SanctitySmoke.started`: `checks+=MySmoke.run(server);`
3. Запустите `CI=true ./gradlew runServer -PsanctitySmoke --no-daemon` и убедитесь,
   что в конце есть `SANCTITY SMOKE PASSED` и `BUILD SUCCESSFUL`.
4. Сохраните результат прогона в файл `*-TEST-RESULTS.txt` — как это сделано для
   `RITUAL-TEST-RESULTS.txt`, `FIRE-TEST-RESULTS.txt`, `TABLE-TEST-RESULTS.txt`.
5. Обновите `VERIFICATION.md`: что проверено, а что осталось непроверенным.

---

## 8. Что тестами не покрывается

Честно фиксируйте это в отчётах:

- графика: анимации, частицы, подсветка, поведение с шейдерами и рендер-модами;
- сеть: несколько игроков, клиент без мода, задержки;
- длительная нагрузка и большие миры;
- совместимость с системами приватов и другими модами.

Такие пункты описываются как **непроверенные** — их проверяет пользователь вручную
(чек-лист: `docs/PLAYTEST-RU.md`).

---

## 9. Готовый текст для передачи другой ИИ

> Проект: Settled Lands, Minecraft 1.21.1, NeoForge 21.1.252, Java 21, Gradle (ModDevGradle).
> Тесты двух уровней:
> 1) unit-тесты JUnit 5 в `src/test/java/dev/settledlands/`, запуск `CI=true ./gradlew build --no-daemon`;
> 2) интеграционные проверки на настоящем выделенном сервере в `src/smoke/java/dev/settledlands/smoke/`,
>    запуск `CI=true ./gradlew runServer -PsanctitySmoke --no-daemon`.
> Интеграционный набор: точка входа `SanctitySmoke` подписана на `ServerStartedEvent`,
> каждый набор — класс с методом `run(MinecraftServer)`, проверки через `check(boolean,String)`,
> в конце пишется маркер `run-smoke/sanctity-smoke-result.txt` со словом PASSED; Gradle-задача
> `runServer` (при `-PsanctitySmoke`) падает, если маркера нет или он не начинается с PASSED.
> Перед первым запуском скопируй `smoke-server/server.properties` в `run-smoke/server.properties`.
> Правила: только настоящие игровые механики (реальные блоки, меню, команды, лут, NBT, молния),
> фейковый игрок через `FakePlayerFactory.getMinecraft(level)` (командам нужен `.withPermission(4)`,
> меню — игрок рядом с блоком и уникальный id меню), время и погода задаются явно
> (`overworldData().setGameTime`, `setDayTime(18000)`, `setWeatherParameters`), долгие интервалы
> не выжидаются, а ускоряются сдвигом времени и ручным вызовом обработчика, за собой проверки
> убирают сущности. Сначала измеряй и печатай факт (как измеритель строк стола в `TableSmoke`,
> отчёт в `run-smoke/table-report.txt`), потом утверждай. Поведение ванильных классов
> проверяй через `javap -p -c -classpath build/moddev/artifacts/neoforge-21.1.252.jar <класс>`,
> а не декомпиляцией. На слабой машине: `CI=true`, JDK 21, не задавать `_JAVA_OPTIONS` с большим
> `-Xmx` (дочерние JVM падают с «Failed to execute tool»), `chmod +x gradlew`.
> Подробности и примеры — в `docs/TESTING-HOWTO-RU.md` этого репозитория.
