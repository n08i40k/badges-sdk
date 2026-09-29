package ru.n08i40k.badges.util

import android.util.Log
import ru.n08i40k.badges.BadgesSdkProvider

internal object Logger {
    @Volatile
    private var suppressFatal = false

    fun info(message: String) {
        Log.i(BadgesSdkProvider.ID, message)
    }

    fun warn(message: String) {
        Log.w(BadgesSdkProvider.ID, message)
    }

    fun fatal(message: String, exception: Throwable, preventEject: Boolean = false) {
        Log.e(BadgesSdkProvider.ID, message, exception)

        if (!suppressFatal && !preventEject) {
            suppressFatal = true

            BadgesSdkProvider.FATAL_EXCEPTION_HANDLER?.invoke(exception)
            runCatching { BadgesSdkProvider.destroy() }
        }
    }

    inline fun tryOrFatal(action: String, crossinline block: () -> Unit): Unit? =
        try {
            block.invoke()
        } catch (e: Throwable) {
            fatal("Failed to $action", e)
            null
        }
}
