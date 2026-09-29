package ru.n08i40k.badges.emoji

import android.view.View
import androidx.annotation.UiThread
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.ActionBarLayout
import org.telegram.ui.ActionBar.INavigationLayout
import org.telegram.ui.DialogsActivity
import org.telegram.ui.LaunchActivity
import ru.n08i40k.badges.api.ViewFactory
import ru.n08i40k.badges.util.`DialogsActivity$viewPage`
import ru.n08i40k.badges.util.`LaunchActivity$actionBarLayout`
import ru.n08i40k.badges.util.`LaunchActivity$layersActionBarLayout`
import ru.n08i40k.badges.util.`LaunchActivity$rightActionBarLayout`
import ru.n08i40k.badges.util.FrameCallbacks
import ru.n08i40k.badges.util.Logger
import ru.n08i40k.badges.util.`MainTabsActivity$getDialogsActivity`
import ru.n08i40k.badges.util.invokeAndCast
import java.lang.ref.Reference
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

internal object EmojiRegistry {
    private const val HOUSEKEEPING_INTERVAL_MS = 5_000L

    // GC кладёт сюда ссылки на собранные Emoji, по ним их EjectData уходят из elements
    private val collected = ReferenceQueue<Emoji>()

    private val elements = ConcurrentHashMap<Reference<out Emoji>, Emoji.EjectData>(128)

    private val touchHandlers = WeakHashMap<View, EmojiTouchHandler>()

    @Volatile
    private var housekeepingActive = false

    private val housekeeping = object : Runnable {
        override fun run() {
            if (!housekeepingActive)
                return

            try {
                removeCollected()
                FrameCallbacks.pruneCollected()
            } catch (e: Throwable) {
                Logger.fatal("Failed to run emoji housekeeping", e, preventEject = true)
                housekeepingActive = false
                return
            }

            AndroidUtilities.runOnUIThread(this, HOUSEKEEPING_INTERVAL_MS)
        }
    }

    // ссылка на Emoji, которую нужно передать в EjectData.drawable
    fun reference(emoji: Emoji): WeakReference<Emoji> = WeakReference(emoji, collected)

    fun add(data: Emoji.EjectData) {
        removeCollected()
        elements[data.drawable] = data
    }

    private fun removeCollected() {
        while (true)
            elements.remove(collected.poll() ?: return)
    }

    fun startHousekeeping() {
        housekeepingActive = true
        AndroidUtilities.runOnUIThread(housekeeping, HOUSEKEEPING_INTERVAL_MS)
    }

    fun stopHousekeeping() {
        housekeepingActive = false
        AndroidUtilities.cancelRunOnUIThread(housekeeping)
    }

    fun attachTouchHandler(view: View, drawable: Emoji) {
        val handler = synchronized(touchHandlers) {
            touchHandlers.getOrPut(view) { EmojiTouchHandler.install(view) }
        }

        handler.register(drawable)
    }

    @UiThread
    fun restoreAll() {
        elements.values.forEach {
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
        for (data in elements.values) {
            val emoji = data.drawable.get() ?: continue

            Logger.tryOrFatal("rebuild badge views") {
                emoji.rebuild()
            }
        }
    }

    // Перевязать views одной фабрики, опционально только для одного пользователя;
    // возвращает true, если хотя бы у одного эмодзи изменилась ширина
    @UiThread
    fun rebindAll(factory: ViewFactory, userId: Long?): Boolean {
        var resized = false

        for (data in elements.values) {
            val emoji = data.drawable.get() ?: continue

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

            for (i in stack.indices) {
                val fragment = stack[i] ?: continue

                if (fragment is DialogsActivity)
                    dialogsActivities.add(fragment)
                else if (fragment.javaClass.name == "org.telegram.ui.MainTabsActivity") {
                    `MainTabsActivity$getDialogsActivity`
                        .getOrNull()
                        ?.invokeAndCast<DialogsActivity>(fragment)
                        ?.let(dialogsActivities::add)
                }
            }
        }

        // Удивительно, что баг проявился только после обновления jar до версии 12.8.0
        // Как это вообще работало?
        (`LaunchActivity$actionBarLayout`.invokeExact(launchActivity) as ActionBarLayout?)
            ?.let(::populateSet)

        (`LaunchActivity$rightActionBarLayout`.invokeExact(launchActivity) as ActionBarLayout?)
            ?.let(::populateSet)

        (`LaunchActivity$layersActionBarLayout`.invokeExact(launchActivity) as ActionBarLayout?)
            ?.let(::populateSet)

        @Suppress("UNCHECKED_CAST")
        val viewPages = dialogsActivities
            .mapNotNull { `DialogsActivity$viewPage`.invokeExact(it) as Array<View?>? }
            .flatMap { it.toSet() }

        for (page in viewPages) {
            val listView = (page as? DialogsActivity.ViewPage)?.listView ?: continue
            val adapter = listView.adapter
            listView.adapter = null
            listView.adapter = adapter
        }
    }
}
