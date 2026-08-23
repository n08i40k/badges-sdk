# Badges SDK

Плагин для exteraGram / AyuGram, который рисует произвольные бейджи рядом с именем
пользователя (список диалогов, сообщения, профиль, шапка чата, поиск) и отдаёт
другим плагинам API для добавления своих бейджей.

Сам SDK ничего не рисует по своей инициативе: он только держит список фабрик
(`ViewFactory`), которые ему регистрируют плагины-потребители.

> **Сейчас доступно только взаимодействие через DEX (Kotlin/Java).**
> API для Python-плагинов будет добавлено в следующих обновлениях.

## Установка

Пользователю нужно поставить `badges-sdk.py` как обычный плагин exteraGram.
Ваш плагин работает и без него — вызовы API просто ничего не делают, пока SDK
не загружен.

## Использование в своём плагине

### 1. Подключить compat-модуль

`compat` — это маленькая обёртка на рефлексии: она сама находит SDK, ждёт его
загрузки, если он поставлен позже вашего плагина, и переживает его выгрузку.
Прямая зависимость на `:api` не нужна.

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
just watch   # live-reload на подключённом устройстве
just ci      # release DEX + compat.aar
just embed   # -> dist/badges-sdk.py с вшитым DEX
```

Прочее:

- `just loc` — перегенерировать i18n-файлы без полной пересборки DEX.
- `just update-apk <apk>` — обновить `libs/Telegram*.jar` из APK хоста.
- `just gen-stubs <rt.jar> <android.jar>` — стабы для автодополнения в Python.

## Лицензия

[MIT](LICENSE)
