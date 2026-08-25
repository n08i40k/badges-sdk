package ru.n08i40k.badges.util

import android.os.Looper
import androidx.annotation.AnyThread
import org.telegram.messenger.AndroidUtilities

@AnyThread
inline fun <R> runOnUIThread(crossinline block: () -> R) =
    AndroidUtilities.runOnUIThread { Logger.tryOrFatal("run on ui thread") { block.invoke() } }

// выполняет блок сразу, если мы уже на UI-потоке, иначе откладывает его
@AnyThread
inline fun <R> runOnUIThreadNow(crossinline block: () -> R) {
    if (Looper.myLooper() === Looper.getMainLooper())
        Logger.tryOrFatal("run on ui thread") { block.invoke() }
    else
        runOnUIThread(block)
}

