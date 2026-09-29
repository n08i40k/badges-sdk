package ru.n08i40k.badges.hook.impl.emoji

import android.text.Layout
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.messenger.MessageObject
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.Cells.DialogCell
import org.telegram.ui.Components.AnimatedEmojiDrawable
import ru.n08i40k.badges.emoji.Emoji
import ru.n08i40k.badges.hook.HookBundle
import ru.n08i40k.badges.hook.InstallHook
import ru.n08i40k.badges.util.`ChatMessageCell$currentNameStatusDrawable`
import ru.n08i40k.badges.util.`ChatMessageCell$currentNameStatusDrawable$$setter`
import ru.n08i40k.badges.util.`ChatMessageCell$drawNameStatus`
import ru.n08i40k.badges.util.`ChatMessageCell$drawNameStatus$$setter`
import ru.n08i40k.badges.util.`ChatMessageCell$nameLayout`
import ru.n08i40k.badges.util.`ChatMessageCell$nameLayout$$setter`
import ru.n08i40k.badges.util.`ChatMessageCell$nameLayoutWidth`
import ru.n08i40k.badges.util.`ChatMessageCell$nameLayoutWidth$$setter`
import ru.n08i40k.badges.util.`ChatMessageCell$nameWidth`
import ru.n08i40k.badges.util.`ChatMessageCell$nameWidth$$setter`
import ru.n08i40k.badges.util.`ChatMessageCell$viaNameWidth`
import ru.n08i40k.badges.util.`ChatMessageCell$viaSpan1`
import ru.n08i40k.badges.util.`ChatMessageCell$viaWidth$$setter`
import ru.n08i40k.badges.util.`DialogCell$FixedWidthSpan$width`
import ru.n08i40k.badges.util.`DialogCell$FixedWidthSpan$width$$setter`
import ru.n08i40k.badges.util.`Theme$chat_namePaint`
import java.lang.ref.WeakReference
import kotlin.math.ceil

internal class ChatMessageCellHookBundle : HookBundle() {
    private var savedInitializationData: Pair<Int, WeakReference<ChatMessageCell>>? = null

    override fun inject(
        before: InstallHook,
        after: InstallHook
    ) {
        // Сообщение в группе
        before(
            ChatMessageCell::class.java.getDeclaredMethod(
                "setMessageObjectInternal",
                MessageObject::class.java
            )
        ) { param ->
            val messageObject = param.args[0] as? MessageObject
                ?: return@before

            if (messageObject.isOut || !messageObject.isFromUser)
                return@before

            savedInitializationData = Pair(
                System.identityHashCode(messageObject),
                WeakReference(param.thisObject as ChatMessageCell)
            )
        }

        before(
            MessageObject::class.java.getDeclaredMethod(
                "isForwarded"
            )
        ) { param ->
            val (savedId, savedCellRef) = savedInitializationData ?: return@before
            val messageObject = param.thisObject as MessageObject

            if (System.identityHashCode(messageObject) != savedId)
                return@before

            savedInitializationData = null

            // here
            val peerUserId =
                if (messageObject.isFromUser)
                    messageObject.messageOwner.from_id.user_id
                else
                    return@before

            val thisObject = ChatMessageCell::class.java.cast(savedCellRef.get() ?: return@before)!!

            val emoji = Emoji.encapsulate(
                thisObject,
                `ChatMessageCell$currentNameStatusDrawable`,
                `ChatMessageCell$currentNameStatusDrawable$$setter`,
                null,
                peerUserId,
                badgeSlot = Emoji.BadgeSlot.STATUS_OR_NAME,
                simpleTextView = null,
            ) ?: return@before

            when {
                `ChatMessageCell$nameLayoutWidth` != null -> fitLegacyNameLayout(thisObject, emoji)
                `ChatMessageCell$drawNameStatus` != null -> fitNameLayout(thisObject, emoji)
                else -> return@before
            }

            thisObject.invalidate()
        }

        // Нажатие по нашему бейджу не должно открывать шторку премиума клиента
        before(
            Class.forName($$"org.telegram.ui.ChatActivity$ChatMessageCellDelegate")
                .getDeclaredMethod(
                    "didPressUserStatus",
                    ChatMessageCell::class.java,
                    TLRPC.User::class.java,
                    TLRPC.Document::class.java,
                    String::class.java,
                )
        ) { param ->
            val cell: Any = param.args[0] as? ChatMessageCell
                ?: return@before

            val emoji =
                `ChatMessageCell$currentNameStatusDrawable`.invokeExact(cell) as AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable? as? Emoji?
                    ?: return@before

            if (!emoji.hasVisibleBadges())
                return@before

            param.args[3] = ""
        }
    }

    // Раскладка с nameLayoutWidth/viaNameWidth: статус рисуется всегда, после имени или на месте
    // FixedWidthSpan в строке "имя via @bot"
    private fun fitLegacyNameLayout(cell: ChatMessageCell, emoji: Emoji) {
        val extraPx = emoji.getAdditionalWidth()

        if (`ChatMessageCell$viaNameWidth`!!.invokeExact(cell) as Int == 0) {
            addNameWidth(cell, extraPx)
            return
        }

        val nameLayoutWidth = `ChatMessageCell$nameLayoutWidth`!!.invokeExact(cell) as Int

        val newLayout = widenNameStatusSpan(cell, extraPx, nameLayoutWidth + extraPx + dp(2f))
            ?: return

        `ChatMessageCell$nameLayoutWidth$$setter`!!.invokeExact(
            cell,
            ceil(newLayout.getLineWidth(0)).toInt()
        )
        `ChatMessageCell$viaWidth$$setter`.invokeExact(cell, extraPx)
    }

    // Раскладка с nameStatusOffsetX (AyuGram 12.10.5): статус рисуется только при drawNameStatus,
    // смещение клиент считает при построении nameLayout
    private fun fitNameLayout(cell: ChatMessageCell, emoji: Emoji) {
        val extraPx = emoji.getAdditionalWidth()

        val nameLayout = `ChatMessageCell$nameLayout`.invokeExact(cell) as? StaticLayout
            ?: return

        val text = nameLayout.text as? Spanned
        val viaSpan = `ChatMessageCell$viaSpan1`?.invokeExact(cell)

        // viaSpan1 не сбрасывается между сообщениями, поэтому проверяем, что он из текущего имени
        val hasVia = text != null && viaSpan != null && text.getSpanStart(viaSpan) >= 0

        if (!hasVia) {
            if (`ChatMessageCell$drawNameStatus`!!.invokeExact(cell) as Boolean) {
                addNameWidth(cell, extraPx)
                return
            }

            if (!emoji.hasVisibleBadges())
                return

            // без статуса клиент не резервирует под него место (20dp) и не рисует drawable
            `ChatMessageCell$drawNameStatus$$setter`!!.invokeExact(cell, true)
            addNameWidth(cell, extraPx + dp(20f))
            return
        }

        // nameStatusOffsetX указывает на начало FixedWidthSpan и после расширения не меняется
        widenNameStatusSpan(cell, extraPx, nameLayout.width + extraPx)
            ?: return

        `ChatMessageCell$viaWidth$$setter`.invokeExact(cell, extraPx)
    }

    private fun addNameWidth(cell: ChatMessageCell, extraPx: Int) {
        val nameWidth = `ChatMessageCell$nameWidth`.invokeExact(cell) as Int
        `ChatMessageCell$nameWidth$$setter`.invokeExact(cell, nameWidth + extraPx)
    }

    // В строке "имя via @bot" место под статус - FixedWidthSpan после имени
    private fun widenNameStatusSpan(
        cell: ChatMessageCell,
        extraPx: Int,
        layoutWidth: Int,
    ): StaticLayout? {
        val nameLayout = `ChatMessageCell$nameLayout`.invokeExact(cell) as? StaticLayout
            ?: return null

        val text = nameLayout.text as? Spanned ?: return null

        val span = text.getSpans(0, text.length, DialogCell.FixedWidthSpan::class.java)
            .lastOrNull()
            ?: return null

        val width = `DialogCell$FixedWidthSpan$width`.invokeExact(span) as Int
        `DialogCell$FixedWidthSpan$width$$setter`.invokeExact(span, width + extraPx)

        val newLayout = StaticLayout(
            text,
            `Theme$chat_namePaint`.invokeExact() as TextPaint,
            layoutWidth,
            Layout.Alignment.ALIGN_NORMAL,
            1.0f,
            0.0f,
            false
        )

        `ChatMessageCell$nameLayout$$setter`.invokeExact(cell, newLayout)

        return newLayout
    }
}
