package ru.n08i40k.badges.hook.impl.emoji

import android.content.Context
import android.graphics.Canvas
import android.view.View
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.messenger.SharedConfig
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.DialogCell
import org.telegram.ui.Components.AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable
import org.telegram.ui.DialogsActivity
import ru.n08i40k.badges.emoji.Emoji
import ru.n08i40k.badges.hook.HookBundle
import ru.n08i40k.badges.hook.InstallHook
import ru.n08i40k.badges.util.`DialogCell$currentDialogId`
import ru.n08i40k.badges.util.`DialogCell$drawVerified`
import ru.n08i40k.badges.util.`DialogCell$emojiStatus`
import ru.n08i40k.badges.util.`DialogCell$emojiStatus$$setter`
import ru.n08i40k.badges.util.`DialogCell$emojiStatusView`
import ru.n08i40k.badges.util.isClientVersionBelow

internal class DialogCellHookBundle : HookBundle() {
    override fun inject(
        before: InstallHook,
        after: InstallHook
    ) {
        // Чат в списке, нужно ещё увеличить bounds по x, иначе текста не будет
        after(
            DialogCell::class.java.getConstructor(
                DialogsActivity::class.java,
                Context::class.java,
                Boolean::class.java,
                Boolean::class.java,
                Int::class.java,
                Theme.ResourcesProvider::class.java
            )
        )
        { param ->
            Emoji.encapsulate(
                param.thisObject,
                `DialogCell$emojiStatus`,
                `DialogCell$emojiStatus$$setter`,
                null,
                0,
                badgeSlot = Emoji.BadgeSlot.STATUS_OR_NAME,
            )
        }

        // Конструктор чата в списке не имеет его в качестве аргумента, он задаётся после
        after(
            DialogCell::class.java.getDeclaredMethod(
                "buildLayout",
            )
        ) { param ->
            val obj = param.thisObject as DialogCell

            (`DialogCell$emojiStatus`.invokeExact(param.thisObject) as SwapAnimatedEmojiDrawable? as? Emoji)
                ?.setPeerUserId(`DialogCell$currentDialogId`.invokeExact(obj) as Long)
        }

        val drawVerified = `DialogCell$drawVerified`
        val statusViewGetter = `DialogCell$emojiStatusView`
        if (statusViewGetter != null && drawVerified != null) {
            after(DialogCell::class.java.getDeclaredMethod("onDraw", Canvas::class.java)) { param ->
                val cell = param.thisObject as DialogCell
                if (!(drawVerified.invokeExact(cell) as Boolean)) return@after
                val emoji = `DialogCell$emojiStatus`.invokeExact(param.thisObject) as SwapAnimatedEmojiDrawable? as? Emoji
                    ?: return@after
                if (!emoji.hasVisibleBadges()) return@after
                val layout = cell.nameLayout ?: return@after
                val statusView = statusViewGetter.invokeExact(cell) as View
                val checkWidth = Theme.dialogs_verifiedDrawable.intrinsicWidth
                statusView.translationX = cell.nameLeft + layout.getLineWidth(0) + dp(6f) + checkWidth + dp(4f)
                statusView.translationY = dp(if (SharedConfig.useThreeLinesLayout) 11f else 13f).toFloat()
                statusView.visibility = View.VISIBLE
            }
        }

        // Фикс отрисовки текста в местах, где размер view ограничен по x.
        // Например, в списке чатов, где у SwapAnimatedEmojiDrawable есть обёртка в виде View,
        // который жёстко ограничен по x.
        if (!isClientVersionBelow("12.2.6")) {
            after(
                DialogCell::class.java.getDeclaredMethod(
                    "onLayout",
                    Boolean::class.java,
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                )
            ) { param ->
                val obj = param.thisObject as DialogCell

                val emojiStatusView =
                    `DialogCell$emojiStatusView`!!.invokeExact(obj) as View

                val emojiStatus =
                    `DialogCell$emojiStatus`.invokeExact(param.thisObject) as SwapAnimatedEmojiDrawable

                val height = dp(22f)

                emojiStatusView.layout(
                    0,
                    0,
                    maxOf(height * 4, emojiStatus.intrinsicWidth),
                    height
                )
            }
        }
    }
}
