package ru.n08i40k.badges.util

import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC
import java.lang.reflect.Method

object BadgesCompat {
    data class ReflectionData(
        // BadgesController
        val badgesController: Any,

        // BadgesController::getBadge(TLObject?)
        val getBadge: Method,

        // BadgesController::getSecondaryBadge(User?)
        val getSecondaryBadge: Method?,

        // BadgeDTO::getDocumentId()
        val getDocumentId: Method
    )

    private var reflectionData: ReflectionData? = null

    // без рефлексии мы не знаем, что лежит в занятом нами слоте, и не имеем права
    // прятать оригинал
    val isAvailable: Boolean
        get() = reflectionData != null

    fun init() {
        // Starting from 12.8.1 BadgesController is unobfuscated again
        if (!isClientVersionBelow("12.6.4") && isClientVersionBelow("12.8.1")) {
            Logger.info("BadgesController is obfuscated in this client, client badges are unreadable")
            return
        }

        try {
            resolve()
        } catch (e: Throwable) {
            Logger.info("Client has no badges support: ${e.message}")
        }
    }

    private fun resolve() {
        val controllerClass = Class.forName("com.exteragram.messenger.badges.BadgesController")
        val badgeClass = Class.forName("com.exteragram.messenger.api.dto.BadgeDTO")


        reflectionData = ReflectionData(
            badgesController = controllerClass
                .getDeclaredField("INSTANCE")
                .get(null)
                ?: throw NullPointerException("Failed to get badges controller instance"),

            getBadge = controllerClass
                .getDeclaredMethod("getBadge", TLObject::class.java),

            getSecondaryBadge = controllerClass
                .takeIf { !isClientVersionBelow("12.8.1") }
                ?.getDeclaredMethod("getSecondaryBadge", TLRPC.User::class.java),

            getDocumentId = badgeClass
                .getDeclaredMethod("getDocumentId")
        )
    }

    fun getDocumentId(obj: TLObject): Long? {
        return with(reflectionData ?: return null) {
            val badge = getBadge.invoke(badgesController, obj)
                ?: return null

            val documentId = getDocumentId.invoke(badge)
                ?: run {
                    reflectionData = null
                    return@with null
                }

            return documentId as Long
        }
    }

    fun hasSecondaryBadge(user: TLRPC.User): Boolean =
        reflectionData?.let { it.getSecondaryBadge?.invoke(it.badgesController, user) } != null
}