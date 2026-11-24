@file:Suppress("ObjectPropertyName")

package ru.n08i40k.badges.util

import android.annotation.SuppressLint
import android.text.TextPaint
import android.view.View
import org.telegram.messenger.BaseController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.UserConfig
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.Cells.DialogCell
import org.telegram.ui.Cells.ProfileSearchCell
import org.telegram.ui.Cells.UserCell
import org.telegram.ui.Components.AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable
import org.telegram.ui.Components.ChatAvatarContainer
import org.telegram.ui.Components.StatusBadgeComponent
import org.telegram.ui.DialogsActivity
import org.telegram.ui.LaunchActivity
import org.telegram.ui.ProfileActivity
import java.lang.invoke.MethodHandle

@JvmField
internal val `ChatAvatarContainer$parentFragment` =
    getFieldGetter(ChatAvatarContainer::class.java, "parentFragment")

@JvmField
internal val `ChatAvatarContainer$titleTextView` =
    getFieldGetter(ChatAvatarContainer::class.java, "titleTextView")

// поля со статусом принимают Any: Emoji.encapsulate работает с ними без знания класса владельца
@JvmField
internal val `ChatAvatarContainer$emojiStatusDrawable` =
    getFieldGetter(ChatAvatarContainer::class.java, "emojiStatusDrawable")
        .retype(SwapAnimatedEmojiDrawable::class.java, Any::class.java)

@JvmField
internal val `ChatAvatarContainer$emojiStatusDrawable$$setter` =
    getFieldSetter(ChatAvatarContainer::class.java, "emojiStatusDrawable")
        .retype(Void.TYPE, Any::class.java, SwapAnimatedEmojiDrawable::class.java)

@JvmField
internal val `ChatMessageCell$currentNameStatusDrawable` =
    getFieldGetter(ChatMessageCell::class.java, "currentNameStatusDrawable")
        .retype(SwapAnimatedEmojiDrawable::class.java, Any::class.java)

@JvmField
internal val `ChatMessageCell$currentNameStatusDrawable$$setter` =
    getFieldSetter(ChatMessageCell::class.java, "currentNameStatusDrawable")
        .retype(Void.TYPE, Any::class.java, SwapAnimatedEmojiDrawable::class.java)

@JvmField
internal val `ChatMessageCell$viaWidth` =
    getFieldGetter(ChatMessageCell::class.java, "viaWidth")

@JvmField
internal val `ChatMessageCell$viaWidth$$setter` =
    getFieldSetter(ChatMessageCell::class.java, "viaWidth")

// pre 12.10.5
@JvmField
internal val `ChatMessageCell$viaNameWidth` =
    getFieldGetterIfExists(ChatMessageCell::class.java, "viaNameWidth")

@JvmField
internal val `ChatMessageCell$nameWidth` =
    getFieldGetter(ChatMessageCell::class.java, "nameWidth")

@JvmField
internal val `ChatMessageCell$nameWidth$$setter` =
    getFieldSetter(ChatMessageCell::class.java, "nameWidth")

@JvmField
internal val `ChatMessageCell$nameLayout` =
    getFieldGetter(ChatMessageCell::class.java, "nameLayout")

@JvmField
internal val `ChatMessageCell$nameLayout$$setter` =
    getFieldSetter(ChatMessageCell::class.java, "nameLayout")

@JvmField
internal val `ChatMessageCell$nameLayoutWidth` =
    getFieldGetterIfExists(ChatMessageCell::class.java, "nameLayoutWidth")

@JvmField
internal val `ChatMessageCell$nameLayoutWidth$$setter` =
    getFieldSetterIfExists(ChatMessageCell::class.java, "nameLayoutWidth")

// 12.10.5 статус рисуется только при drawNameStatus
@JvmField
internal val `ChatMessageCell$drawNameStatus` =
    getFieldGetterIfExists(ChatMessageCell::class.java, "drawNameStatus")

@JvmField
internal val `ChatMessageCell$drawNameStatus$$setter` =
    getFieldSetterIfExists(ChatMessageCell::class.java, "drawNameStatus")

@JvmField
internal val `ChatMessageCell$viaSpan1` =
    getFieldGetterIfExists(ChatMessageCell::class.java, "viaSpan1")
        ?.retype(Any::class.java, ChatMessageCell::class.java)

@JvmField
internal val `DialogCell$FixedWidthSpan$width` =
    getFieldGetter(DialogCell.FixedWidthSpan::class.java, "width")

@JvmField
internal val `DialogCell$FixedWidthSpan$width$$setter` =
    getFieldSetter(DialogCell.FixedWidthSpan::class.java, "width")

@JvmField
internal val `Theme$chat_namePaint` =
    getFieldGetter(Theme::class.java, "chat_namePaint")
        .retype(TextPaint::class.java)

@JvmField
internal val `DialogCell$emojiStatus` =
    getFieldGetter(DialogCell::class.java, "emojiStatus")
        .retype(SwapAnimatedEmojiDrawable::class.java, Any::class.java)

@JvmField
internal val `DialogCell$emojiStatus$$setter` =
    getFieldSetter(DialogCell::class.java, "emojiStatus")
        .retype(Void.TYPE, Any::class.java, SwapAnimatedEmojiDrawable::class.java)

@JvmField
internal val `DialogCell$drawVerified` =
    getFieldGetterIfExists(DialogCell::class.java, "drawVerified")

@JvmField
internal val `DialogCell$currentDialogId` =
    getFieldGetter(DialogCell::class.java, "currentDialogId")

// отсутствует в клиентах ниже 12.2.6
@JvmField
internal val `DialogCell$emojiStatusView` =
    getFieldGetterIfExists(DialogCell::class.java, "emojiStatusView")

@JvmField
internal val `ProfileActivity$userId` =
    getFieldGetter(ProfileActivity::class.java, "userId")

@JvmField
internal val `ProfileActivity$nameTextView` =
    getFieldGetter(ProfileActivity::class.java, "nameTextView")
        .retype(Any::class.java, ProfileActivity::class.java)

internal fun profileNameTextView(profile: ProfileActivity): SimpleTextView? {
    return when (val nameField: Any? = `ProfileActivity$nameTextView`.invokeExact(profile)) {
        is SimpleTextView -> nameField
        is Array<*> -> nameField.getOrNull(1) as? SimpleTextView
        else -> null
    }
}

// Emoji.encapsulate принимает владельца поля как Any и ожидает исходный тип значения.
private fun MethodHandle.withObjectReceiver(): MethodHandle =
    asType(type().changeParameterType(0, Any::class.java))

@JvmField
internal val `ProfileActivity$emojiStatusDrawable` =
    getFieldGetter(ProfileActivity::class.java, "emojiStatusDrawable")
        .withObjectReceiver()

@JvmField
internal val `ProfileActivity$emojiStatusDrawable$$setter` =
    getFieldSetter(ProfileActivity::class.java, "emojiStatusDrawable")
        .withObjectReceiver()

@JvmField
internal val `ProfileActivity$badgeDrawable$$setter` =
    getFieldSetterIfExists(ProfileActivity::class.java, "badgeDrawable")
        ?.withObjectReceiver()

@JvmField
internal val `ProfileActivity$badgeDrawable` =
    `ProfileActivity$badgeDrawable$$setter`
        ?.let {
            getFieldGetterIfExists(ProfileActivity::class.java, "badgeDrawable")
                ?.withObjectReceiver()
        }

internal fun profileDrawableIndex(getter: MethodHandle): Int? =
    if (getter.type().returnType().isArray) 1 else null

@Suppress("UNCHECKED_CAST")
internal fun profileDrawable(profile: Any, getter: MethodHandle?): SwapAnimatedEmojiDrawable? {
    if (getter == null)
        return null

    val index = profileDrawableIndex(getter)

    return if (index == null)
        getter.invokeExact(profile) as SwapAnimatedEmojiDrawable?
    else
        (getter.invokeExact(profile) as Array<SwapAnimatedEmojiDrawable?>?)
            ?.getOrNull(index)
}

@JvmField
internal val `ProfileActivity$updateProfileData` = runCatching {
    ProfileActivity::class.java.getDeclaredMethod("updateProfileData", Boolean::class.java)
}

@JvmField
internal val `ProfileSearchCell$statusDrawable` =
    getFieldGetter(ProfileSearchCell::class.java, "statusDrawable")
        .retype(SwapAnimatedEmojiDrawable::class.java, Any::class.java)

@JvmField
internal val `ProfileSearchCell$statusDrawable$$setter` =
    getFieldSetter(ProfileSearchCell::class.java, "statusDrawable")
        .retype(Void.TYPE, Any::class.java, SwapAnimatedEmojiDrawable::class.java)

@JvmField
internal val `StatusBadgeComponent$statusDrawable` =
    getFieldGetter(StatusBadgeComponent::class.java, "statusDrawable")
        .retype(SwapAnimatedEmojiDrawable::class.java, Any::class.java)

@JvmField
internal val `StatusBadgeComponent$statusDrawable$$setter` =
    getFieldSetter(StatusBadgeComponent::class.java, "statusDrawable")
        .retype(Void.TYPE, Any::class.java, SwapAnimatedEmojiDrawable::class.java)

@JvmField
internal val `UserCell$currentObject` =
    getFieldGetter(UserCell::class.java, "currentObject")

@JvmField
internal val `UserCell$nameTextView` =
    getFieldGetter(UserCell::class.java, "nameTextView")

@JvmField
internal val `UserCell$emojiStatus` =
    getFieldGetter(UserCell::class.java, "emojiStatus")
        .retype(SwapAnimatedEmojiDrawable::class.java, Any::class.java)

@JvmField
internal val `UserCell$emojiStatus$$setter` =
    getFieldSetter(UserCell::class.java, "emojiStatus")
        .retype(Void.TYPE, Any::class.java, SwapAnimatedEmojiDrawable::class.java)

@JvmField
internal val `BaseController$currentAccount` =
    getFieldGetter(BaseController::class.java, "currentAccount")

@JvmField
internal val `SwapAnimatedEmojiDrawable$parentView` =
    getFieldGetter(SwapAnimatedEmojiDrawable::class.java, "parentView")

@JvmField
internal val `SwapAnimatedEmojiDrawable$size` =
    getFieldGetter(SwapAnimatedEmojiDrawable::class.java, "size")

@JvmField
internal val `SwapAnimatedEmojiDrawable$invalidateRunnable` =
    getFieldGetterIfExists(SwapAnimatedEmojiDrawable::class.java, "invalidateRunnable")

@JvmField
internal val `SimpleTextView$rightDrawable` =
    getFieldGetter(SimpleTextView::class.java, "rightDrawable")

@JvmField
internal val `SimpleTextView$rightDrawable2` =
    getFieldGetter(SimpleTextView::class.java, "rightDrawable2")

@JvmField
internal val `SwapAnimatedEmojiDrawable$$fields` =
    getAccessibleFields(SwapAnimatedEmojiDrawable::class.java)

@JvmField
internal val `DialogsActivity$viewPage` =
    getFieldGetter(DialogsActivity::class.java, "viewPages")
        .retype(Array<View>::class.java, DialogsActivity::class.java)

@JvmField
internal val `LaunchActivity$actionBarLayout` =
    getFieldGetter(LaunchActivity::class.java, "actionBarLayout")

@JvmField
internal val `LaunchActivity$rightActionBarLayout` =
    getFieldGetter(LaunchActivity::class.java, "rightActionBarLayout")

@JvmField
internal val `LaunchActivity$layersActionBarLayout` =
    getFieldGetter(LaunchActivity::class.java, "layersActionBarLayout")

// org.telegram.ui.MainTabsActivity, отсутствует в части клиентов
@JvmField
internal val `MainTabsActivity$getDialogsActivity` = runCatching {
    Class.forName("org.telegram.ui.MainTabsActivity")
        .getDeclaredMethod("getDialogsActivity")
}

@SuppressLint("PrivateApi")
@JvmField
internal val `View$ListenerInfo` =
    runCatching { Class.forName($$"android.view.View$ListenerInfo") }

@JvmField
internal val `SimpleTextView$rightDrawableOnClickListener` =
    getFieldGetter(SimpleTextView::class.java, "rightDrawableOnClickListener")

@JvmField
internal val `View$mListenerInfo` =
    runCatching {
        getFieldGetter(View::class.java, "mListenerInfo")
            .retype(Any::class.java, View::class.java)
    }

@JvmField
internal val `View$ListenerInfo$mOnTouchListener` =
    runCatching {
        getFieldGetter(`View$ListenerInfo`.getOrThrow(), "mOnTouchListener")
            .retype(View.OnTouchListener::class.java, Any::class.java)
    }

@JvmField
internal val `MessagesController$users` =
    getFieldGetter(MessagesController::class.java, "users")
        .retype(Any::class.java, MessagesController::class.java)

@JvmField
internal val `UserConfig$Instance` =
    getFieldGetter(UserConfig::class.java, "Instance")
