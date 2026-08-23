package ru.n08i40k.badges.util

import ru.n08i40k.badges.LogReceiver
import ru.n08i40k.badges.Plugin
import ru.n08i40k.badges.extension.format
import java.util.concurrent.ThreadLocalRandom

object Logger {
    private val ID = ThreadLocalRandom.current()
        .nextInt()
        .toHexString(HexFormat {
            upperCase = true

            number {
                minLength = 4
            }
        })
        .take(4)

    @Volatile
    private var receiver: LogReceiver? = null

    @Volatile
    private var suppressFatal = false

    fun setReceiver(receiver: LogReceiver) {
        this.receiver = receiver
    }

    fun info(message: String) {
        try {
            receiver?.onReceiveValue("DEX:$ID $message")
        } catch (_: Throwable) {
            Plugin.eject()
        }
    }

    fun fatal(message: String, exception: Throwable, preventEject: Boolean = false) {
        try {
            receiver?.onReceiveValue("DEX:$ID $message")
            receiver?.onReceiveValue("DEX:$ID ${exception.format()}")
        } catch (e: Throwable) {
            Plugin.eject()
            throw e
        }

        if (!suppressFatal && !preventEject)
            Plugin.eject()
    }

    inline fun tryOrFatal(action: String, crossinline block: () -> Unit): Unit? =
        try {
            block.invoke()
        } catch (e: Throwable) {
            fatal("Failed to $action", e)
            null
        }

    fun cleanup() {
        receiver = null
    }
}
