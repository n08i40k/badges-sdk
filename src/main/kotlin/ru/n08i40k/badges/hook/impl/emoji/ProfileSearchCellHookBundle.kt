package ru.n08i40k.badges.hook.impl.emoji

import org.telegram.tgnet.TLRPC
import org.telegram.ui.Cells.ProfileSearchCell
import ru.n08i40k.badges.emoji.Emoji
import ru.n08i40k.badges.hook.HookBundle
import ru.n08i40k.badges.hook.InstallHook
import ru.n08i40k.badges.util.`ProfileSearchCell$statusDrawable`
import ru.n08i40k.badges.util.`ProfileSearchCell$statusDrawable$$setter`

internal class ProfileSearchCellHookBundle : HookBundle() {
    override fun inject(
        before: InstallHook,
        after: InstallHook
    ) {
        // Чат в списке результатов поиска
        after(
            // additional argument at index 1 for badgeDTO
            ProfileSearchCell::class.java.declaredMethods
                .find { it.name == "updateStatus" }!!
        )
        { param ->
            val thisObject = param.thisObject as ProfileSearchCell

            val user = param.args[2] as? TLRPC.User
                ?: return@after

            Emoji.encapsulate(
                thisObject,
                `ProfileSearchCell$statusDrawable`,
                `ProfileSearchCell$statusDrawable$$setter`,
                null,
                user.id,
                badgeSlot = Emoji.BadgeSlot.STATUS,
            )
        }
    }
}
