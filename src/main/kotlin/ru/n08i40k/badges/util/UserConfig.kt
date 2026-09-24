package ru.n08i40k.badges.util

import org.telegram.messenger.UserConfig

@Suppress("UNCHECKED_CAST")
internal val MAX_ACCOUNT_COUNT =
    Array<UserConfig>::class.java.cast(
        getField(
            UserConfig::class.java,
            "Instance"
        ).get(null)
    )!!.size