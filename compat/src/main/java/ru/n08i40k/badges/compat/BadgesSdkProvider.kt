package ru.n08i40k.badges.compat

import android.view.View
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

object BadgesSdkProvider {
    private const val SDK_PROPS_KEY = "ru.n08i40k.badges.BadgesService"
    private const val WAITER_PROPS_KEY_PREFIX = "ru.n08i40k.badges.waiter."

    private const val SDK_CLASS = "ru.n08i40k.badges.api.BadgesSdk"
    private const val VIEW_FACTORY_CLASS = "ru.n08i40k.badges.api.ViewFactory"

    private val lock = Any()

    private val FAILED = Any()

    private var logger: ((String, Throwable?) -> Unit)? = null
    private var pluginId: String? = null
    private var waiter: Runnable? = null
    private var mirror: SdkMirror? = null

    private val factories = LinkedHashSet<BadgesViewFactory>()
    private val proxies = HashMap<BadgesViewFactory, Any>()

    fun setLogger(logger: ((message: String, error: Throwable?) -> Unit)?) {
        synchronized(lock) { this.logger = logger }
    }

    val isAvailable: Boolean
        get() = synchronized(lock) { mirror() != null }

    val sdkVersion: String?
        get() = synchronized(lock) {
            val mirror = mirror() ?: return null
            mirror.call(mirror.getVersion) as? String
        }

    fun addBadgeFactory(pluginId: String, factory: BadgesViewFactory) {
        synchronized(lock) {
            this.pluginId = pluginId
            factories.add(factory)
            installWaiter(pluginId)
            sync()
        }
    }

    fun removeBadgeFactory(factory: BadgesViewFactory) {
        synchronized(lock) {
            factories.remove(factory)

            val proxy = proxies.remove(factory) ?: return
            val mirror = mirror() ?: return
            mirror.call(mirror.removeBadgeFactory, proxy)
        }
    }

    fun scheduleRebind(factory: BadgesViewFactory) {
        synchronized(lock) {
            val mirror = mirror() ?: return
            val proxy = proxies[factory] ?: return
            mirror.call(mirror.scheduleRebind, proxy)
        }
    }

    fun scheduleRebind(factory: BadgesViewFactory, userId: Long) {
        synchronized(lock) {
            val mirror = mirror() ?: return
            val proxy = proxies[factory] ?: return
            mirror.call(mirror.scheduleRebindForUser, proxy, userId)
        }
    }

    fun shutdown() {
        synchronized(lock) {
            val mirror = mirror()

            if (mirror != null) {
                for (proxy in proxies.values)
                    mirror.call(mirror.removeBadgeFactory, proxy)
            }

            val waiter = this.waiter
            val pluginId = this.pluginId

            if (waiter != null && pluginId != null) {
                val props = System.getProperties()
                val key = WAITER_PROPS_KEY_PREFIX + pluginId

                synchronized(props) {
                    if (props[key] === waiter)
                        props.remove(key)
                }
            }

            this.waiter = null
            this.mirror = null
            factories.clear()
            proxies.clear()
        }
    }

    private fun installWaiter(pluginId: String) {
        if (waiter != null)
            return

        val waiter = Runnable { synchronized(lock) { sync() } }
            .also { this.waiter = it }

        val props = System.getProperties()
        synchronized(props) { props[WAITER_PROPS_KEY_PREFIX + pluginId] = waiter }
    }

    private fun sync() {
        val mirror = mirror() ?: return
        val pluginId = this.pluginId ?: return

        for (factory in factories) {
            if (factory in proxies)
                continue

            val proxy = mirror.proxyFor(factory)

            if (mirror.call(mirror.addBadgeFactory, pluginId, proxy) === FAILED)
                continue

            proxies[factory] = proxy
        }
    }

    private fun mirror(): SdkMirror? {
        val sdk = System.getProperties()[SDK_PROPS_KEY]

        if (sdk == null) {
            mirror = null
            return null
        }

        val current = mirror

        if (current != null) {
            if (current.sdk === sdk)
                return current

            proxies.clear()
        }

        return try {
            SdkMirror(sdk).also { mirror = it }
        } catch (e: Throwable) {
            log("Badges SDK api is not compatible with this plugin", e)
            mirror = null
            null
        }
    }

    private fun log(message: String, error: Throwable? = null) {
        logger?.invoke(message, error)
    }

    private class SdkMirror(val sdk: Any) {
        private val loader: ClassLoader = sdk.javaClass.classLoader!!
        private val sdkClass: Class<*> = Class.forName(SDK_CLASS, false, loader)

        val viewFactoryClass: Class<*> = Class.forName(VIEW_FACTORY_CLASS, false, loader)

        val getVersion: Method = sdkClass.getMethod("getVersion")
        val addBadgeFactory: Method =
            sdkClass.getMethod("addBadgeFactory", String::class.java, viewFactoryClass)
        val removeBadgeFactory: Method =
            sdkClass.getMethod("removeBadgeFactory", viewFactoryClass)
        val scheduleRebind: Method = sdkClass.getMethod("scheduleRebind", viewFactoryClass)
        val scheduleRebindForUser: Method =
            sdkClass.getMethod("scheduleRebind", viewFactoryClass, java.lang.Long.TYPE)

        fun call(method: Method, vararg args: Any?): Any? =
            try {
                method.invoke(sdk, *args)
            } catch (e: InvocationTargetException) {
                log("Badges SDK call ${method.name} failed", e.targetException)
                FAILED
            } catch (e: Throwable) {
                log("Badges SDK call ${method.name} failed", e)
                FAILED
            }

        fun proxyFor(factory: BadgesViewFactory): Any =
            Proxy.newProxyInstance(loader, arrayOf(viewFactoryClass)) { proxy, method, args ->
                when (method.name) {
                    "create" -> factory.create(args!![0] as View, (args[1] as Number).toInt())
                    "bind" -> factory.bind(args!![0] as View, (args[1] as Number).toLong())
                    "destroy" -> factory.destroy(args!![0] as View)
                    "equals" -> proxy === args?.get(0)
                    "hashCode" -> System.identityHashCode(proxy)
                    "toString" -> "BadgesViewFactoryProxy($factory)"
                    // неизвестный метод (другая версия)
                    else -> null
                }
            }
    }
}
