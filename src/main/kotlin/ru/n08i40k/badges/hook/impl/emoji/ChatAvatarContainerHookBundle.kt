package ru.n08i40k.badges.hook.impl.emoji

import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.AnimatedEmojiDrawable
import org.telegram.ui.Components.ChatAvatarContainer
import ru.n08i40k.badges.emoji.Emoji
import ru.n08i40k.badges.hook.HookBundle
import ru.n08i40k.badges.hook.InstallHook
import ru.n08i40k.badges.util.`ChatAvatarContainer$emojiStatusDrawable`
import ru.n08i40k.badges.util.`ChatAvatarContainer$emojiStatusDrawable$$setter`
import ru.n08i40k.badges.util.`ChatAvatarContainer$parentFragment`
import ru.n08i40k.badges.util.`ChatAvatarContainer$titleTextView`

internal class ChatAvatarContainerHookBundle : HookBundle() {
    override fun inject(
        before: InstallHook,
        after: InstallHook
    ) {
        // Заголовок открытого лс с пользователем
        after(
            ChatAvatarContainer::class.java
                .getDeclaredMethods()
                .filter { it.name == "setTitle" }
                .maxByOrNull { it.parameterCount }!!
        ) { param ->
            val thisObject = param.thisObject as ChatAvatarContainer

            val parentFragment =
                `ChatAvatarContainer$parentFragment`.invokeExact(thisObject) as? ChatActivity
                    ?: return@after

            val dialogId = parentFragment.dialogId

            if (dialogId < 0)
                return@after

            val titleTextView =
                `ChatAvatarContainer$titleTextView`.invokeExact(thisObject) as SimpleTextView

            val newDrawable = Emoji.encapsulate(
                thisObject,
                `ChatAvatarContainer$emojiStatusDrawable`,
                `ChatAvatarContainer$emojiStatusDrawable$$setter`,
                null,
                dialogId,
                badgeSlot = Emoji.BadgeSlot.SEPARATE,
                simpleTextView = titleTextView,
            ) ?: return@after

            if (titleTextView.rightDrawable !== newDrawable && titleTextView.rightDrawable is AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable)
                titleTextView.rightDrawable = newDrawable
        }
    }
}
