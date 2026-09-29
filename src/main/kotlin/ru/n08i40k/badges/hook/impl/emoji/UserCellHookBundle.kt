package ru.n08i40k.badges.hook.impl.emoji

import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.Cells.UserCell
import ru.n08i40k.badges.emoji.Emoji
import ru.n08i40k.badges.hook.HookBundle
import ru.n08i40k.badges.hook.InstallHook
import ru.n08i40k.badges.util.`UserCell$currentObject`
import ru.n08i40k.badges.util.`UserCell$emojiStatus`
import ru.n08i40k.badges.util.`UserCell$emojiStatus$$setter`
import ru.n08i40k.badges.util.`UserCell$nameTextView`

internal class UserCellHookBundle : HookBundle() {
    override fun inject(
        before: InstallHook,
        after: InstallHook
    ) {
        // Пользователь в списке участников группы
        after(
            UserCell::class.java.getDeclaredMethod(
                "update",
                Int::class.java
            )
        ) { param ->
            val thisObject = param.thisObject as UserCell

            @Suppress("USELESS_CAST") // or WrongMethodTypeException
            val currentUser = (`UserCell$currentObject`.invokeExact(thisObject) as Any?) as? TLRPC.User
                ?: return@after

            val nameTextView = `UserCell$nameTextView`.invokeExact(thisObject) as SimpleTextView

            val emoji = Emoji.encapsulate(
                thisObject,
                `UserCell$emojiStatus`,
                `UserCell$emojiStatus$$setter`,
                null,
                currentUser.id,
                badgeSlot = Emoji.BadgeSlot.SEPARATE,
                simpleTextView = nameTextView,
            )

            nameTextView.rightDrawable = emoji
        }
    }
}
