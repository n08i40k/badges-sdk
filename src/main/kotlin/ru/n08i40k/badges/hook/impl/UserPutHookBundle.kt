package ru.n08i40k.badges.hook.impl

import org.telegram.messenger.BaseController
import org.telegram.messenger.MessagesController
import org.telegram.tgnet.TLRPC
import ru.n08i40k.badges.hook.HookBundle
import ru.n08i40k.badges.hook.InstallHook
import ru.n08i40k.badges.util.`BaseController$currentAccount`
import ru.n08i40k.badges.util.UserPatcher
import ru.n08i40k.badges.util.UserPatcher.isPatched

internal class UserPutHookBundle : HookBundle() {
    override fun inject(
        before: InstallHook,
        after: InstallHook
    ) {
        before(
            MessagesController::class.java.getDeclaredMethod(
                "putUser",
                TLRPC.User::class.java,
                Boolean::class.java,
                Boolean::class.java,
            )
        ) { param ->
            val user = param.args[0] as? TLRPC.User
                ?: return@before

            if (user.isPatched())
                return@before

            val messagesController = param.thisObject as BaseController
            val accountId = `BaseController$currentAccount`.invokeExact(messagesController) as Int

            UserPatcher.patchUserOnAccount(accountId, user)
        }
    }
}