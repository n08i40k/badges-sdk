package ru.n08i40k.badges

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import org.jetbrains.annotations.Blocking
import ru.n08i40k.badges.api.BadgesSdk
import ru.n08i40k.badges.emoji.EmojiRegistry
import ru.n08i40k.badges.hook.impl.UserPutHookBundle
import ru.n08i40k.badges.hook.impl.emoji.ChatAvatarContainerHookBundle
import ru.n08i40k.badges.hook.impl.emoji.ChatMessageCellHookBundle
import ru.n08i40k.badges.hook.impl.emoji.DialogCellHookBundle
import ru.n08i40k.badges.hook.impl.emoji.ProfileActivityHookBundle
import ru.n08i40k.badges.hook.impl.emoji.ProfileSearchCellHookBundle
import ru.n08i40k.badges.hook.impl.emoji.StatusBadgeComponentHookBundle
import ru.n08i40k.badges.hook.impl.emoji.UserCellHookBundle
import ru.n08i40k.badges.util.BadgesCompat
import ru.n08i40k.badges.util.Logger
import ru.n08i40k.badges.util.UserPatcher
import java.lang.reflect.Member
import kotlin.time.Instant

public class BadgesSdkProvider private constructor() {
    public companion object {
        public const val ID: String = "badges-sdk"

        @Volatile
        private var INSTANCE: BadgesSdkProvider? = null

        @Volatile
        private var WAS_INITIALIZED: Boolean = false

        @Volatile
        internal var DEBUG: Boolean = false

        @Volatile
        internal var FATAL_EXCEPTION_HANDLER: ((Throwable) -> Unit)? = null

        @JvmStatic
        public fun getBuildDate(): String = Instant
            .fromEpochMilliseconds(BuildConfig.BUILD_TIME)
            .toString()

        @Synchronized
        @JvmStatic
        public fun setDebug(debug: Boolean) {
            DEBUG = debug
        }

        @Synchronized
        @JvmStatic
        public fun setFatalExceptionHandler(handler: (Throwable) -> Unit) {
            FATAL_EXCEPTION_HANDLER = handler
        }

        @Synchronized
        @Blocking
        @JvmStatic
        public fun create(): BadgesSdk {
            if (WAS_INITIALIZED)
                throw IllegalStateException("Cannot init Badges SDK twice")

            WAS_INITIALIZED = true

            Logger.tryOrFatal("create and init provider") {
                val provider = BadgesSdkProvider()
                    .also { INSTANCE = it }

                provider.onCreate()
            }

            return BadgesSdkService as BadgesSdk
        }

        @Blocking
        @JvmStatic
        @Synchronized
        public fun destroy() {
            val instance = INSTANCE ?: return
            Logger.tryOrFatal("Failed to eject plugin", instance::onDestroy)

            // clears refs
            INSTANCE = null
            FATAL_EXCEPTION_HANDLER = null
        }
    }

    // installed hooks, unhooked on eject
    private val hooks: ArrayList<XC_MethodHook.Unhook> = arrayListOf()

    private fun onCreate() {
        BadgesCompat.init()
        UserPatcher.patchAllUsers()

        Logger.tryOrFatal(
            "hook methods",
            ::hookMethods
        )
    }

    @Blocking
    private fun onDestroy() {
        hooks.forEach {
            Logger.tryOrFatal(
                "unhook method ${it.hookedMethod}",
                it::unhook
            )
        }

        hooks.clear()

        EmojiRegistry.restoreAll()
        BadgesSdkService.clear()

        UserPatcher.restoreAllUsers()
    }

    private fun hookMethods() {
        fun add(method: Member, hook: XC_MethodHook) {
            hooks.add(XposedBridge.hookMethod(method, hook))
        }

        fun before(method: Member, callback: (XC_MethodHook.MethodHookParam) -> Unit) {
            val action = "run $method before-call hook"

            add(
                method,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        Logger.tryOrFatal(action) { callback(param) }
                    }
                }
            )
        }

        fun after(method: Member, callback: (XC_MethodHook.MethodHookParam) -> Unit) {
            val action = "run $method after-call hook"

            add(
                method,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        Logger.tryOrFatal(action) { callback(param) }
                    }
                }
            )
        }

        val bundles = arrayOf(
            UserPutHookBundle(),
            DialogCellHookBundle(),
            ChatMessageCellHookBundle(),
            UserCellHookBundle(),
            ProfileActivityHookBundle(),
            ProfileSearchCellHookBundle(),
            StatusBadgeComponentHookBundle(),
            ChatAvatarContainerHookBundle(),
        )

        bundles.forEach { it.inject(::before, ::after) }
    }
}
