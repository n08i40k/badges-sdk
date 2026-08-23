package ru.n08i40k.badges.util

import org.telegram.messenger.MessagesController
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLRPC
import java.util.concurrent.ConcurrentHashMap

object UserPatcher {
    private const val FLAG_PATCHED: Int = 1 shl 28

    private fun applyUserState(user: TLRPC.User): Boolean {
        if (user.premium || user.flags2 and FLAG_PATCHED != 0)
            return false

        user.flags2 = user.flags2 or FLAG_PATCHED
        user.premium = true

        return true
    }

    fun patchUser(accountId: Int, user: TLRPC.User) {
        val messagesController = MessagesController.getInstance(accountId)

        if (applyUserState(user))
            messagesController.putUser(user, false, true)
    }

    fun patchAllAccounts() {
        for (accountId in 0..<UserConfig.MAX_ACCOUNT_COUNT) {
            if (!UserConfig.getInstance(accountId).isClientActivated)
                continue

            patchUsers(accountId)
        }
    }

    fun patchUsers(accountId: Int) {
        val messagesController = MessagesController.getInstance(accountId)

        @Suppress("UNCHECKED_CAST")
        val users = getField(messagesController.javaClass, "users")
            .get(messagesController) as? ConcurrentHashMap<Long, TLRPC.User> ?: return

        users.forEach { (_, user) ->
            if (applyUserState(user))
                messagesController.putUser(user, false, true)
        }
    }

    fun cleanup() {
        for (accountId in 0..<UserConfig.MAX_ACCOUNT_COUNT) {
            val config = UserConfig.getInstance(accountId)

            if (!config.isClientActivated)
                continue

            val messagesController = MessagesController.getInstance(accountId)

            @Suppress("UNCHECKED_CAST")
            val users = getField(messagesController.javaClass, "users")
                .get(messagesController) as? ConcurrentHashMap<Long, TLRPC.User> ?: continue

            users.forEach { (_, user) ->
                // премиум, который выдали не мы, снимать нельзя
                if (!user.isPatched())
                    return@forEach

                user.flags2 = user.flags2 and FLAG_PATCHED.inv()
                user.premium = false

                messagesController.putUser(user, false, true)
            }
        }
    }

    fun TLRPC.User.isPatched(): Boolean = (flags2 and FLAG_PATCHED) != 0
}