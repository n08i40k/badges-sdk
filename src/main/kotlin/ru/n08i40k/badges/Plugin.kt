package ru.n08i40k.badges

import android.webkit.ValueCallback
import androidx.annotation.MainThread
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import org.jetbrains.annotations.Blocking
import ru.n08i40k.badges.api.BadgesSdk
import ru.n08i40k.badges.emoji.EmojiRegistry
import ru.n08i40k.badges.hook.impl.PluginUnloadHook
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

typealias LogReceiver = ValueCallback<String>

class Plugin private constructor() {
    @Suppress("unused")
    companion object {
        // prefix of the keys under which consumers register their wake-up Runnable
        private const val WAITER_PROPS_KEY_PREFIX = "ru.n08i40k.badges.waiter."

        // key the SDK publishes itself under; consumers resolve it reflectively, so it
        // is part of the contract and must stay in sync with :compat
        private const val SDK_PROPS_KEY = "ru.n08i40k.badges.BadgesService"

        @Volatile
        private var INSTANCE: Plugin? = null

        @Volatile
        private var WAS_INJECTED: Boolean = false

        private var VERSION: String? = null

        // id плагина, чей питоновский лоадер загрузил этот движок
        @Volatile
        private var LOADED_BY: String? = null

        internal fun getInstance(): Plugin = INSTANCE!!

        @JvmStatic
        fun getVersion(): String? = VERSION

        @JvmStatic
        fun getLoadedBy(): String? = LOADED_BY

        @JvmStatic
        fun getBuildDate(): String = Instant
            .fromEpochMilliseconds(BuildConfig.BUILD_TIME.toLong())
            .toString()

        @Synchronized
        @Blocking
        @JvmStatic
        fun inject(
            version: String,
            loadedBy: String,
            logReceiver: LogReceiver,
        ) {
            VERSION = version
            LOADED_BY = loadedBy
            Logger.setReceiver(logReceiver)

            // release builds never eject, so a plugin reload can land here with
            // the instance of the previous load still alive
            if (INSTANCE != null) {
                Logger.info("Plugin is already injected, reusing the existing instance")
                return
            }

            if (WAS_INJECTED)
                throw IllegalStateException("Cannot inject plugin from same class-loader twice")

            WAS_INJECTED = true

            Logger.tryOrFatal("create and inject plugin") {
                val plugin = Plugin()
                    .also { INSTANCE = it }

                plugin.onInject()
            }

            notifyWaiters()
        }

        // consumers loaded before the SDK leave a Runnable in the system properties
        // instead of polling for it
        private fun notifyWaiters() {
            val props = System.getProperties()

            val waiters = synchronized(props) {
                props.keys
                    .filterIsInstance<String>()
                    .filter { it.startsWith(WAITER_PROPS_KEY_PREFIX) }
                    .mapNotNull { props[it] as? Runnable }
            }

            for (waiter in waiters) {
                try {
                    waiter.run()
                } catch (e: Throwable) {
                    // a broken consumer must not take the SDK down with it
                    Logger.fatal("Badges SDK waiter failed", e, preventEject = true)
                }
            }
        }

        @MainThread
        @Blocking
        @JvmStatic
        @Synchronized
        fun eject(): Boolean {
            if (!BuildConfig.DEBUG) {
                // hooks installed by a release build are never rolled back: the
                // loader keeps the engine alive until the client restarts
                Logger.info("Eject is disabled in release builds")
                return false
            }

            Logger.tryOrFatal("Failed to eject plugin") {
                INSTANCE?.onEject()
            }

            val props = System.getProperties()

            synchronized(props) {
                if (props[SDK_PROPS_KEY] === BadgesSdkService)
                    props.remove(SDK_PROPS_KEY)
            }

            INSTANCE = null
            LOADED_BY = null

            return true
        }
    }

    // installed hooks, unhooked on eject
    val hooks: ArrayList<XC_MethodHook.Unhook> = arrayListOf()

    private fun onInject() {
        Logger.info("Injected!")

        BadgesCompat.init()

        UserPatcher.patchAllAccounts()

        Logger.tryOrFatal(
            "hook methods",
            ::hookMethods
        )

        val props = System.getProperties()

        // prevent two plugin injects concurrently (from different class-loaders)
        synchronized(props) {
            props[SDK_PROPS_KEY] = BadgesSdkService as BadgesSdk
        }
    }

    @Blocking
    private fun onEject() {
        Logger.info("onEject called!")

        hooks.forEach {
            Logger.tryOrFatal(
                "unhook method ${it.hookedMethod}",
                it::unhook
            )
        }

        hooks.clear()

        EmojiRegistry.restoreAll()
        BadgesSdkService.clear()

        UserPatcher.cleanup()

        Logger.cleanup()
    }

    private fun hookMethods() {
        fun add(method: Member, hook: XC_MethodHook) {
            hooks.add(XposedBridge.hookMethod(method, hook))
        }

        fun before(method: Member, callback: (XC_MethodHook.MethodHookParam) -> Unit) {
            add(
                method,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        Logger.tryOrFatal("run $method before-call hook") { callback(param) }
                    }
                }
            )
        }

        fun after(method: Member, callback: (XC_MethodHook.MethodHookParam) -> Unit) {
            add(
                method,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        Logger.tryOrFatal("run $method after-call hook") { callback(param) }
                    }
                }
            )
        }

        val bundles = listOf(
            PluginUnloadHook(),
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
