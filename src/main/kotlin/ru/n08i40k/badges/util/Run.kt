package ru.n08i40k.badges.util

import android.os.Looper
import androidx.annotation.AnyThread
import kotlinx.coroutines.runBlocking
import org.telegram.messenger.AndroidUtilities

@AnyThread
inline fun <R> runOnUIThread(crossinline block: () -> R) =
    AndroidUtilities.runOnUIThread { Logger.tryOrFatal("run on ui thread") { block.invoke() } }

@AnyThread
inline fun <R> runOnMainThread(crossinline block: () -> R) = runOnUIThread(block)

// выполняет блок сразу, если мы уже на UI-потоке, иначе откладывает его
@AnyThread
inline fun <R> runOnUIThreadNow(crossinline block: () -> R) {
    if (Looper.myLooper() === Looper.getMainLooper())
        Logger.tryOrFatal("run on ui thread") { block.invoke() }
    else
        runOnUIThread(block)
}

@AnyThread
inline fun <R> runBlockingOnUIThread(crossinline block: suspend () -> R) {
    AndroidUtilities.runOnUIThread {
        Logger.tryOrFatal("run on ui thread") {
            runBlocking { block.invoke() }
        }
    }
}

@AnyThread
inline fun <R> runBlockingOnMainThread(crossinline block: suspend () -> R) =
    runBlockingOnUIThread(block)
