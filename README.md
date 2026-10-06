# GeelyNavbar — своя боковая панель для ГУ Geely / ecarx

Модуль LSPosed для ГУ Geely на Android 9: свои кнопки на штатной боковой панели. Настраивается в приложении, применяется сразу. Выключили — панель снова заводская.

> **English.** LSPosed module for Geely / ecarx head units (Android 9): rebuild the stock side panel — buttons, tap and long-press actions, icons, climate widget position. Applied live; the UI is in Russian.

![Экран настройки](docs/editor.png)

## Возможности

- До 12 кнопок в любом порядке, виджет климата можно передвинуть или убрать.
- Действие на нажатие и на удержание: «Назад», «Домой», «Недавние», «Приложения», «Авто», «Климат», запуск приложения, **сплит** из двух приложений.
- Иконки: штатные, свои или иконка приложения; масштаб 30–200 %.
- Отступы колонки, пресеты «Заводской» и «Расширенный» (как мод SX11), свои пресеты.

## Требования

- ГУ ecarx на Android 9. Проверено на Geely Emgrand (SS11) / Belgee S50 — со штатной панелью и с модом SX11.
- Root: Magisk с Zygisk.
- LSPosed или [Vector](https://github.com/JingMatrix/Vector).

## Установка

1. Скачайте APK со страницы [Releases](../../releases) и установите.
2. В LSPosed (Vector) → «Модули» включите «Боковая панель» и отметьте в нём SystemUI (`com.android.systemui`), если он не отмечен.
3. Перезагрузите ГУ.
4. Откройте «Боковая панель», соберите панель и нажмите «Применить».

Если статус вверху не «Работает» — нажмите на него, там подсказка.

## Настройка

- Нажмите на кнопку панели слева — справа откроются её настройки: «Нажатие», «Удержание», «Вид».
- Удерживайте кнопку, чтобы перетащить её или убрать в лоток. Новые кнопки — из лотка.
- Штриховка сверху и снизу — отступы, нажмите на неё для точной настройки.
- Ошиблись — «Вернуть» внизу или ↶ вверху.

| Вид кнопки | Отступы | Пресеты |
|---|---|---|
| ![Вид](docs/look.png) | ![Отступы](docs/paddings.png) | ![Пресеты](docs/presets.png) |

![Панель на ГУ](docs/hu-panel.png)

## Отключение

Выключатель вверху → «Применить». Или выключите модуль в LSPosed и перезагрузите ГУ.

## Статус

Работает на ГУ: своя панель и откат, виджет климата, «Назад», «Приложения», запуск приложений.
Ещё не проверено в машине: сплит, окно климата, «Недавние», «Авто», «Домой» со сторонним лаунчером, день/ночь, CarPlay.
Нашли проблему — откройте [issue](../../issues).

<details>
<summary><b>Как это работает</b></summary>

Панель рисует плагин ecarx `com.ecarx.systemui.plugin` внутри процесса SystemUI, поэтому скоуп модуля — `com.android.systemui`. Хук на `NavigationBarView.onFinishInflate` кладёт в `mNavContent` свою колонку и переносит в неё виджет климата (`hvacBar`), а `onUIModeChanged` перерисовывает иконки под день и ночь. При любой ошибке остаётся штатная панель.

Приложение хранит конфиг в `MODE_WORLD_READABLE` prefs, хук читает его через `XSharedPreferences`. «Применить» шлёт broadcast `RELOAD`, хук пересобирает панель и отвечает `PONG` со статусом.

Действия выполняются в процессе SystemUI. Сплит (`hook/SplitScreen.kt`) раскладывает уже открытые приложения скрытыми вызовами `IActivityManager`, не перезапуская их.

</details>

<details>
<summary><b>Формат конфига</b></summary>

Один JSON под ключом `config` (`NavConfig.kt`):

```json
{
  "version": 1,
  "enabled": true,
  "paddingTop": 23,
  "paddingBottom": 34,
  "items": [
    {"type": "button", "tap": {"type": "app", "package": "ru.yandex.yandexnavi"},
     "long": {"type": "split", "left": {"type": "app", "package": "ru.yandex.yandexnavi"}, "right": {"type": "app", "package": "ru.yandex.music"}},
     "icon": {"type": "named", "name": "nb_map"}},
    {"type": "hvac", "scale": 100},
    {"type": "button", "tap": {"type": "back"}, "icon": {"type": "auto"}, "scale": 110}
  ]
}
```

- Действия: `none`, `back`, `home`, `apps`, `car`, `climate`, `recents`, `app` (+ `package`, `activity`), `split` (+ `left`, `right`).
- Иконки: `auto`, `named` (+ `name`: `ic_nav_*` или `nb_*`), `app` (+ `package`, `activity`).
- `scale` 30–200 %, отступы 0–300 dp. Неизвестное отбрасывается или заменяется значением по умолчанию.

</details>

<details>
<summary><b>Сборка и релизы</b></summary>

JDK 17 и Android SDK.

```sh
./gradlew assembleRelease            # APK
./gradlew testDebugUnitTest          # JVM-тесты
./gradlew connectedDebugAndroidTest  # на эмуляторе с Android 9
```

Для тестов сплита на эмуляторе: `adb shell settings put global hidden_api_policy_p_apps 1`.

Версия из git: `versionCode` — число коммитов, `versionName` — последний тег `vX.Y.Z`.

Релиз: push тега `vX.Y.Z` → GitHub Actions собирает APK, подписывает release-ключом из секретов (`RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`) и публикует. Локально без ключа APK подписывается debug-ключом и не встанет поверх версии из Releases. Подписать локально: задайте `RELEASE_KEYSTORE_FILE` и `RELEASE_KEYSTORE_PASSWORD`.

</details>

## Лицензия

[MIT](LICENSE). Проект не связан с Geely и ecarx. Используйте на свой риск и не настраивайте панель на ходу.
