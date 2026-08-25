# Badges SDK

Движок для exteraGram / AyuGram, который рисует произвольные бейджи рядом с именем
пользователя (список диалогов, сообщения, профиль, шапка чата, поиск) и отдаёт
плагинам API для добавления своих бейджей.

Сам SDK ничего не рисует по своей инициативе: он только держит список фабрик
(`ViewFactory`), которые ему регистрируют плагины-потребители.

> **Сейчас доступно только взаимодействие через DEX (Kotlin/Java).**
> API для Python-плагинов будет добавлено в следующих обновлениях.

## Установка

SDK встраивается прямо в ваш плагин. Из релиза нужны два файла:

- `badges-sdk-loader.py` — питоновский лоадер с **уже вшитым** DEX движка;
- `badges-sdk-compat.aar` — обёртка для вашего Kotlin/Java-кода.

(`badges-sdk.dex` там же — это тот же движок отдельным файлом, для тех, кто
хочет встроить его как-то иначе.)

Содержимое `badges-sdk-loader.py` целиком (от `# === BADGES SDK LOADER BEGIN ===`
до `# === BADGES SDK LOADER END ===`, вместе с длинным base64-комментарием в конце)
копируется в ваш `.py`. Лоадер читает DEX из исходника того файла, в который его
вставили, так что резать блок нельзя.

## Загрузка движка

```python
class MyPlugin(BasePlugin):
    def on_plugin_load(self):
        self.badges = BadgesSdkLoader(__id__, logger=self.log)
        result = self.badges.load()

        if not result.usable:
            self.log(f"Badges SDK is unavailable: {result}")

        # регистрировать фабрики можно в любом случае: если движок загрузил
        # другой плагин, ваш compat найдёт его сам
        ...

    def on_plugin_unload(self):
        self.badges.unload()
```

Движок в процессе всегда один — его грузит первый успевший плагин, остальные
пользуются уже загруженным. `load()` возвращает `BadgesSdkLoadResult`:

| `status`         | что произошло                                                  | `version` / `loaded_by`            |
| ---------------- | -------------------------------------------------------------- | ---------------------------------- |
| `loaded`         | движок загружен этим плагином                                   | ваша версия / ваш `__id__`         |
| `already_loaded` | уже загружена совместимая версия (такая же или выше)            | её версия / id загрузившего плагина |
| `outdated`       | уже загружена версия старее вашей                               | её версия / id загрузившего плагина |
| `incompatible`   | уже загружена версия с другим мажором (breaking change)         | её версия / id загрузившего плагина |
| `failed`         | загрузить не удалось, причина в `result.error`                  | `None`                             |

Полезные сокращения: `result.usable` (`loaded` или `already_loaded` — бейджи
будут работать), `result.owned` (движок ваш) и `loader.loaded_engine`
(`(версия, id владельца)` движка в процессе или `None`).

При `outdated` / `incompatible` обновить движок на лету нельзя: старый уже
поставил свои хуки. Покажите пользователю, что нужен перезапуск клиента, и
назовите плагин из `loaded_by` — обновлять надо его.

`unload()` возвращает `True`, только если движок действительно выгружен из
памяти (release-сборка движок не выгружает — он живёт до перезапуска клиента).

### Отдельный плагин `badges-sdk`

`remove_standalone_plugin()` удаляет у пользователя отдельно установленный
плагин `badges-sdk`. Метод **необязательный**: лоадер сам его никогда не
вызывает, вызывать его или нет — решает ваш плагин.

```python
# по желанию, до load()
self.badges.remove_standalone_plugin()
```

Вызывать его нужно до `load()`: пока этот плагин установлен, он грузит свой
собственный движок и занимает процесс. Метод возвращает `True`, если плагин был
найден и удалён (файл, настройки и регистрация в exteraGram сносятся вместе с
ним).

Движок, который он успел загрузить, остаётся в памяти до перезапуска клиента,
поэтому `load()` в этой же сессии всё ещё может вернуть `already_loaded` /
`outdated` с `loaded_by == "badges-sdk"`.

## Использование в своём плагине

### 1. Подключить compat-модуль

`compat` — это маленькая обёртка на рефлексии: она сама находит SDK, ждёт его
загрузки, если он поставлен позже вашего плагина, и переживает его выгрузку.
Прямая зависимость на `:api` не нужна.

Возьмите `badges-sdk-compat.aar` из релиза или соберите его сами:

```sh
# в репозитории Badges SDK
just compat /path/to/my-plugin/libs/badges-compat.aar
```

```kotlin
// build.gradle.kts вашего плагина
dependencies {
    implementation(files("libs/badges-compat.aar"))
}
```

Классы `compat` можно спокойно релоцировать (shade) вместе с остальными
зависимостями — с SDK они общаются только по строковым именам.

### 2. Написать фабрику бейджа

```kotlin
import android.view.View
import ru.n08i40k.badges.compat.BadgesViewFactory

object MyBadgeFactory : BadgesViewFactory {
    // parent — view хоста, в которой будет отрисован бейдж.
    // Возвращённая view в иерархию не попадает, поэтому drawable нужно
    // привязывать именно к parent.
    override fun create(parent: View, heightPx: Int): View =
        MyBadgeView(parent.context, heightPx)

    // вызывается для каждого пользователя; false — бейдж этому юзеру не показывать
    override fun bind(view: View, userId: Long): Boolean {
        val badge = badgeFor(userId) ?: return false
        (view as MyBadgeView).setBadge(badge)
        return true
    }

    // SDK выбросил view — отвязать всё, что к ней привязано
    override fun destroy(view: View) {
        (view as MyBadgeView).cancelLoading()
    }
}
```

### 3. Зарегистрировать её

```kotlin
import ru.n08i40k.badges.compat.BadgesSdkProvider

// pluginId должен совпадать с __id__ вашего .py — по нему SDK
// автоматически чистит фабрики при выгрузке плагина
BadgesSdkProvider.setLogger { message, error -> Logger.warn(message, error) }
BadgesSdkProvider.addBadgeFactory("my-plugin", MyBadgeFactory)
```

Регистрировать можно в любой момент, даже до загрузки SDK: вызов запомнится и
применится, как только SDK появится.

### 4. Обновлять и выгружать

```kotlin
// данные изменились — перевызвать bind() для всех показанных бейджей
BadgesSdkProvider.scheduleRebind(MyBadgeFactory)

// то же самое, но только для одного пользователя
BadgesSdkProvider.scheduleRebind(MyBadgeFactory, userId)

// убрать одну фабрику
BadgesSdkProvider.removeBadgeFactory(MyBadgeFactory)

// при выгрузке плагина — снять всё и отписаться от ожидания SDK
BadgesSdkProvider.shutdown()
```

Полезные проверки:

```kotlin
BadgesSdkProvider.isAvailable  // SDK загружен?
BadgesSdkProvider.sdkVersion   // версия SDK или null
```

## Сборка самого SDK

Требуется `java` (JDK 21), `uv`, `just`, `adb`.

```sh
just dex     # debug-сборка DEX
just watch   # live-reload dev-плагина на подключённом устройстве
just ci      # release DEX + compat.aar
just embed   # -> dist/badges-sdk-loader.py с вшитым DEX
```

Исходник лоадера — `loader/badges_sdk.py`, `dev/badges-sdk-dev.py` — dev-плагин,
который грузит движок через тот же самый лоадер (`just watch` подставляет
лоадер и debug-DEX в него на лету).

Прочее:

- `just update-apk <apk>` — обновить `libs/Telegram*.jar` из APK хоста.
- `just gen-stubs <rt.jar> <android.jar>` — стабы для автодополнения в Python.

## Лицензия

[MIT](LICENSE)
