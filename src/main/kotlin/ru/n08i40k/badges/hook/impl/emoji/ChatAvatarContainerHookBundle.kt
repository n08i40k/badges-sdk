package ru.n08i40k.badges.hook.impl.emoji

import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.AnimatedEmojiDrawable
import org.telegram.ui.Components.ChatAvatarContainer
import ru.n08i40k.badges.emoji.Emoji
import ru.n08i40k.badges.hook.HookBundle
import ru.n08i40k.badges.hook.InstallHook
import ru.n08i40k.badges.util.getAs
import ru.n08i40k.badges.util.getAsUnchecked
import ru.n08i40k.badges.util.getField

class ChatAvatarContainerHookBundle : HookBundle() {
    companion object Fields {
        private val CLASS = ChatAvatarContainer::class.java

        val PARENT_FRAGMENT = getField(CLASS, "parentFragment")
        val TITLE_TEXT_VIEW = getField(CLASS, "titleTextView")
        val EMOJI_STATUS_DRAWABLE = getField(CLASS, "emojiStatusDrawable")
    }

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
            val thisObject = param.thisObject

            val dialogId = PARENT_FRAGMENT.getAs<ChatActivity>(thisObject)
                ?.dialogId
                ?.takeIf { it >= 0 }
                ?: return@after

            val titleTextView = TITLE_TEXT_VIEW.getAsUnchecked<SimpleTextView>(thisObject)

            val newDrawable = Emoji.encapsulate(
                thisObject,
                EMOJI_STATUS_DRAWABLE,
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
