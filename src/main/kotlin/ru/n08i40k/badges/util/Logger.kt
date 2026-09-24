package ru.n08i40k.badges.util

import android.util.Log
import ru.n08i40k.badges.BadgesSdkProvider

internal object Logger {
    @Volatile
    private var suppressFatal = false

    fun info(message: String) {
        try {
            Log.i(BadgesSdkProvider.ID, message)
        } catch (_: Throwable) {
            BadgesSdkProvider.destroy()
        }
    }

    fun fatal(message: String, exception: Throwable, preventEject: Boolean = false) {
        try {
            Log.e(BadgesSdkProvider.ID, message, exception)
        } catch (e: Throwable) {
            BadgesSdkProvider.destroy()
            throw e
        }

        if (!suppressFatal && !preventEject)
            BadgesSdkProvider.destroy()
    }

    inline fun tryOrFatal(action: String, crossinline block: () -> Unit): Unit? =
        try {
            block.invoke()
        } catch (e: Throwable) {
            fatal("Failed to $action", e)
            null
        }
}
