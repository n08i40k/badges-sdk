package ru.n08i40k.badges.hook.impl.emoji

import android.graphics.drawable.Drawable
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.Components.AnimatedEmojiDrawable
import org.telegram.ui.ProfileActivity
import ru.n08i40k.badges.emoji.Emoji
import ru.n08i40k.badges.hook.HookBundle
import ru.n08i40k.badges.hook.InstallHook
import ru.n08i40k.badges.util.`ProfileActivity$badgeDrawable`
import ru.n08i40k.badges.util.`ProfileActivity$badgeDrawable$$setter`
import ru.n08i40k.badges.util.`ProfileActivity$emojiStatusDrawable`
import ru.n08i40k.badges.util.`ProfileActivity$emojiStatusDrawable$$setter`
import ru.n08i40k.badges.util.`ProfileActivity$nameTextView`
import ru.n08i40k.badges.util.`ProfileActivity$userId`
import java.lang.invoke.MethodHandle

internal class ProfileActivityHookBundle : HookBundle() {
    private companion object Fields {
        val CLASS = ProfileActivity::class.java

        val HOST_FIELDS = listOfNotNull(
            `ProfileActivity$emojiStatusDrawable` to `ProfileActivity$emojiStatusDrawable$$setter`,
            `ProfileActivity$badgeDrawable` to `ProfileActivity$badgeDrawable$$setter`
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun getDrawable(fragment: Any, field: MethodHandle): Drawable? =
        (field.invokeExact(fragment) as Array<AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable?>)[1]

    override fun inject(
        before: InstallHook,
        after: InstallHook
    ) {
        // Профиль пользователя
        after(
            ProfileActivity::class.java.getDeclaredMethod(
                "updateProfileData",
                Boolean::class.java,
            )
        ) { param ->
            val thisObject = param.thisObject as ProfileActivity

            val userId = `ProfileActivity$userId`.invokeExact(thisObject) as Long

            if (userId < 0)
                return@after

            @Suppress("UNCHECKED_CAST")
            val nameTextView = (`ProfileActivity$nameTextView`
                .invokeExact(thisObject) as? Array<SimpleTextView?>)
                ?.get(1)
                ?: return@after

            // при пустом статусе клиент отдаёт правому drawable бейдж, а не emojiStatusDrawable
            val hostField = HOST_FIELDS
                .firstOrNull { (getter, _) ->
                    val drawable = getDrawable(thisObject, getter ?: return@firstOrNull false)

                    drawable != null
                            && (drawable === nameTextView.rightDrawable
                            || drawable === nameTextView.rightDrawable2)
                }
                ?.let { (getter, setter) -> getter!! to setter!! }
                ?: return@after


            val isSecondary =
                getDrawable(thisObject, hostField.first) === nameTextView.rightDrawable2

            HOST_FIELDS
                .filter { it !== hostField }
                .forEach { (getter, _) ->
                    val drawable = getDrawable(thisObject, getter ?: return@forEach) as? Emoji
                    drawable?.setPeerUserId(0L)
                }

            val emoji = Emoji.encapsulate(
                thisObject,
                hostField.first,
                hostField.second,
                1,
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
