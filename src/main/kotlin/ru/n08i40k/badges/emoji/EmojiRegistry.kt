package ru.n08i40k.badges.emoji

import android.view.View
import androidx.annotation.UiThread
import org.telegram.ui.ActionBar.ActionBarLayout
import org.telegram.ui.ActionBar.INavigationLayout
import org.telegram.ui.DialogsActivity
import org.telegram.ui.LaunchActivity
import ru.n08i40k.badges.api.ViewFactory
import ru.n08i40k.badges.util.Logger
import ru.n08i40k.badges.util.getAs
import ru.n08i40k.badges.util.getField
import java.lang.reflect.Method
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

object EmojiRegistry {
    // DialogsActivity
    private val VIEW_PAGES = getField(DialogsActivity::class.java, "viewPages")

    // LaunchActivity
    private val ACTION_BAR_LAYOUT = getField(LaunchActivity::class.java, "actionBarLayout")
    private val RIGHT_ACTION_BAR_LAYOUT = getField(LaunchActivity::class.java, "rightActionBarLayout")
    private val LAYERS_ACTION_BAR_LAYOUT = getField(LaunchActivity::class.java, "layersActionBarLayout")

    // org.telegram.ui.MainTabsActivity, отсутствует в части клиентов
    val GET_DIALOGS_ACTIVITY: Method by lazy {
        Class.forName("org.telegram.ui.MainTabsActivity")
            .getDeclaredMethod("getDialogsActivity")
    }

    private val elements = ConcurrentHashMap.newKeySet<Emoji.EjectData>(128)

    private val touchHandlers = WeakHashMap<View, EmojiTouchHandler>()

    fun add(data: Emoji.EjectData) = elements.add(data)

    fun attachTouchHandler(view: View, drawable: Emoji) {
        val handler = synchronized(touchHandlers) {
            touchHandlers.getOrPut(view) { EmojiTouchHandler.install(view) }
        }

        handler.register(drawable)
    }

    @UiThread
    fun restoreAll() {
        elements.forEach {
            Logger.tryOrFatal("restore original streak emoji") {
                it.restore()
            }
        }

        elements.clear()

        synchronized(touchHandlers) {
            touchHandlers.forEach { (view, handler) ->
                Logger.tryOrFatal("restore original touch listener") {
                    handler.restore(view)
                }
            }

            touchHandlers.clear()
        }
    }

    // пересоздать кеш views у всех живых эмодзи (например, после изменения списка фабрик)
    @UiThread
    fun rebuildAll() {
        val it = elements.iterator()

        while (it.hasNext()) {
            val emoji = it.next().drawable.get() ?: run {
                it.remove()
                continue
            }

            Logger.tryOrFatal("rebuild badge views") {
                emoji.rebuild()
            }
        }
    }

    // перепривязать views одной фабрики, опционально только для одного пользователя;
    // возвращает true, если хотя бы у одного эмодзи изменилась ширина
    @UiThread
    fun rebindAll(factory: ViewFactory, userId: Long?): Boolean {
        var resized = false

        val it = elements.iterator()

        while (it.hasNext()) {
            val emoji = it.next().drawable.get() ?: run {
                it.remove()
                continue
            }

            Logger.tryOrFatal("rebind badge views") {
                if (emoji.rebindFactory(factory, userId))
                    resized = true
            }
        }

        return resized
    }

    fun refreshDialogCells() {
        val launchActivity = LaunchActivity.instance
        val dialogsActivities = mutableSetOf<DialogsActivity>()

        fun populateSet(layout: INavigationLayout) {
            val stack = layout.fragmentStack

            for (i in 0..<stack.size) {
                val fragment = stack[i] ?: continue

                if (fragment is DialogsActivity)
                    dialogsActivities.add(fragment)
                else if (fragment.javaClass.name == "org.telegram.ui.MainTabsActivity") {
                    (GET_DIALOGS_ACTIVITY.invoke(fragment) as? DialogsActivity)
                        ?.let(dialogsActivities::add)
                }
            }
        }

        // Удивительно, что баг проявился только после обновления jar до версии 12.8.0
        // Как это вообще работало?
        ACTION_BAR_LAYOUT.getAs<ActionBarLayout>(launchActivity)?.let(::populateSet)
        RIGHT_ACTION_BAR_LAYOUT.getAs<ActionBarLayout>(launchActivity)?.let(::populateSet)
        LAYERS_ACTION_BAR_LAYOUT.getAs<ActionBarLayout>(launchActivity)?.let(::populateSet)

        @Suppress("UNCHECKED_CAST")
        val viewPages = dialogsActivities
            .mapNotNull { VIEW_PAGES.getAs<Array<View?>>(it) }
            .flatMap { it.toSet() }

        for (page in viewPages) {
            val listView = (page as? DialogsActivity.ViewPage)?.listView ?: continue
            val adapter = listView.adapter
            listView.adapter = null
            listView.adapter = adapter
        }
    }
}
