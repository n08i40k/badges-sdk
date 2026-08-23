package ru.n08i40k.badges.hook.impl.emoji

import android.view.View
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.StatusBadgeComponent
import ru.n08i40k.badges.emoji.Emoji
import ru.n08i40k.badges.hook.HookBundle
import ru.n08i40k.badges.hook.InstallHook
import ru.n08i40k.badges.util.getField

class StatusBadgeComponentHookBundle : HookBundle() {
    companion object Fields {
        private val CLASS = StatusBadgeComponent::class.java

        val STATUS_DRAWABLE = getField(CLASS, "statusDrawable")
    }

    override fun inject(
        before: InstallHook,
        after: InstallHook
    ) {
        // Эмодзи пользователя в просмотрах сторисов (мб ещё где-то, но я не видел)
        after(
            StatusBadgeComponent::class.java.getDeclaredConstructor(
                View::class.java,
                Int::class.java
            )
        ) { param ->
            val thisObject = param.thisObject as StatusBadgeComponent

            Emoji.encapsulate(
                thisObject,
                STATUS_DRAWABLE,
                null,
                0,
                badgeSlot = Emoji.BadgeSlot.STATUS,
            )
        }

        after(
            StatusBadgeComponent::class.java.getDeclaredMethod(
                "updateDrawable",
                TLRPC.User::class.java,
                TLRPC.Chat::class.java,
                Int::class.java,
                Boolean::class.java
            )
        )
        { param ->
            val thisObject = param.thisObject as StatusBadgeComponent

            val user = param.args[0] as? TLRPC.User
                ?: return@after

            // update user id
            Emoji.encapsulate(
                thisObject,
                STATUS_DRAWABLE,
                null,
                user.id,
                badgeSlot = Emoji.BadgeSlot.STATUS,
            )
        }
    }
}
