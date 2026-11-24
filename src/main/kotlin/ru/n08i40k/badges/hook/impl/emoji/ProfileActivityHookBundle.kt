package ru.n08i40k.badges.hook.impl.emoji

import android.graphics.drawable.Drawable
import org.telegram.ui.ProfileActivity
import ru.n08i40k.badges.emoji.Emoji
import ru.n08i40k.badges.hook.HookBundle
import ru.n08i40k.badges.hook.InstallHook
import ru.n08i40k.badges.util.`ProfileActivity$badgeDrawable`
import ru.n08i40k.badges.util.`ProfileActivity$badgeDrawable$$setter`
import ru.n08i40k.badges.util.`ProfileActivity$emojiStatusDrawable`
import ru.n08i40k.badges.util.`ProfileActivity$emojiStatusDrawable$$setter`
import ru.n08i40k.badges.util.`ProfileActivity$updateProfileData`
import ru.n08i40k.badges.util.`ProfileActivity$userId`
import ru.n08i40k.badges.util.profileDrawable
import ru.n08i40k.badges.util.profileDrawableIndex
import ru.n08i40k.badges.util.profileNameTextView

internal class ProfileActivityHookBundle : HookBundle() {
    override fun inject(
        before: InstallHook,
        after: InstallHook
    ) {
        // Профиль пользователя
        after(`ProfileActivity$updateProfileData`.getOrThrow()) { param ->
            val thisObject = param.thisObject as ProfileActivity

            val userId = `ProfileActivity$userId`.invokeExact(thisObject) as Long

            if (userId < 0)
                return@after

            val nameTextView = profileNameTextView(thisObject)
                ?: return@after

            val rightDrawable = nameTextView.rightDrawable
            val rightDrawable2 = nameTextView.rightDrawable2

            fun isDisplayed(drawable: Drawable?): Boolean =
                drawable != null && (drawable === rightDrawable || drawable === rightDrawable2)

            val status = profileDrawable(thisObject, `ProfileActivity$emojiStatusDrawable`)
            val badge = profileDrawable(thisObject, `ProfileActivity$badgeDrawable`)

            // При пустом статусе клиент может показывать бейдж в правом слоте.
            val usesStatus = when {
                isDisplayed(status) -> true
                isDisplayed(badge) -> false
                else -> return@after
            }

            val getter =
                if (usesStatus)
                    `ProfileActivity$emojiStatusDrawable`
                else
                    `ProfileActivity$badgeDrawable`
                        ?: return@after

            val setter =
                if (usesStatus)
                    `ProfileActivity$emojiStatusDrawable$$setter`
                else
                    `ProfileActivity$badgeDrawable$$setter`
                        ?: return@after

            val isSecondary = (if (usesStatus) status else badge) === rightDrawable2

            val other =
                if (usesStatus)
                    badge
                else
                    status

            (other as? Emoji)
                ?.setPeerUserId(0L)

            val emoji = Emoji.encapsulate(
                thisObject,
                getter,
                setter,
                profileDrawableIndex(getter),
                userId,
                badgeSlot = Emoji.BadgeSlot.SEPARATE,
                simpleTextView = nameTextView,
            ) ?: return@after

            if (isSecondary) {
                if (nameTextView.rightDrawable2 !== emoji)
                    nameTextView.rightDrawable2 = emoji
            } else if (nameTextView.rightDrawable !== emoji) {
                nameTextView.rightDrawable = emoji
            }
        }
    }
}
