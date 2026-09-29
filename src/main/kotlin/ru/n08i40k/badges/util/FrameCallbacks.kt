package ru.n08i40k.badges.util

import android.util.SparseArray
import androidx.annotation.UiThread
import org.telegram.ui.Components.AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable
import java.lang.reflect.Field
import java.lang.reflect.Method

internal object FrameCallbacks {
    private const val CHOREOGRAPHER_CLASS = "org.telegram.messenger.utils.Choreographer60FpsContent"
    private const val REFERENCE_LIST_CLASS = "me.vkryl.core.reference.ReferenceList"

    private val GROUP_LIST_FIELDS =
        listOf("callbacks", "runnableCallbacks", "runnableCallbacksOnce")

    private class Ticker(
        val instance: Any,
        val groups: SparseArray<*>,
        val removeFrameCallback: Method,
        val groupLists: List<Field>,
        val isEmpty: Method,
    )

    private val ticker: Ticker? by lazy(LazyThreadSafetyMode.NONE) {
        try {
            val choreographer = Class.forName(CHOREOGRAPHER_CLASS)
            val group = Class.forName($$"$$CHOREOGRAPHER_CLASS$CallbackGroup")

            val instance = choreographer.getDeclaredMethod("getInstance").invoke(null)!!

            Ticker(
                instance = instance,
                groups = choreographer.getDeclaredField("mGroups")
                    .apply { isAccessible = true }
                    .get(instance) as SparseArray<*>,
                removeFrameCallback = choreographer
                    .getDeclaredMethod("removeFrameCallback", Runnable::class.java),
                groupLists = GROUP_LIST_FIELDS.mapNotNull { name ->
                    runCatching { group.getDeclaredField(name).apply { isAccessible = true } }
                        .onFailure { Logger.warn("Field $group.$name is not available: ${it.message}") }
                        .getOrNull()
                },
                isEmpty = Class.forName(REFERENCE_LIST_CLASS)
                    .getDeclaredMethod("isEmpty"),
            )
        } catch (e: Throwable) {
            Logger.warn("$CHOREOGRAPHER_CLASS is not available: ${e.message}")
            null
        }
    }

    // отписать drawable от тикера: detach() клиента этого не делает
    @UiThread
    fun unsubscribe(drawable: SwapAnimatedEmojiDrawable) {
        val ticker = ticker
            ?: return

        val getter = `SwapAnimatedEmojiDrawable$invalidateRunnable`
            ?: return

        val runnable = getter.invokeExact(drawable) as? Runnable?
            ?: return

        ticker.removeFrameCallback.invoke(ticker.instance, runnable)
    }

    // удалить из списков тикера ссылки на собранные GC колбэки
    @UiThread
    fun pruneCollected() {
        val ticker = ticker ?: return
        val groups = ticker.groups

        for (i in 0 until groups.size()) {
            val group = groups.valueAt(i)

            for (list in ticker.groupLists)
                list.get(group)?.let { ticker.isEmpty.invoke(it) }
        }
    }
}
