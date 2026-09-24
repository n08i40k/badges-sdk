package ru.n08i40k.badges.util

import android.os.Looper
import org.telegram.messenger.AndroidUtilities

// выполняет блок сразу, если мы уже на UI-потоке, иначе откладывает его
internal inline fun <R> runOnUIThreadNow(crossinline block: () -> R) {
    val threadBlock: () -> Unit = { Logger.tryOrFatal("run on ui thread") { block.invoke() } }

    if (Looper.myLooper() === Looper.getMainLooper())
        threadBlock.invoke()
    else
        AndroidUtilities.runOnUIThread(threadBlock)
}

