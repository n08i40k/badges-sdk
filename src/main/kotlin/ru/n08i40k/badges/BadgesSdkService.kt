package ru.n08i40k.badges

import ru.n08i40k.badges.BuildConfig.BUILD_VERSION
import ru.n08i40k.badges.api.BadgesSdk
import ru.n08i40k.badges.api.ViewFactory
import ru.n08i40k.badges.emoji.EmojiRegistry
import ru.n08i40k.badges.util.Logger
import ru.n08i40k.badges.util.runOnUIThreadNow

internal object BadgesSdkService : BadgesSdk {
    private val emojiFactories = ArrayList<ViewFactory>(16)

    // неизменяемый снимок для чтения из drawable без блокировок
    @Volatile
    private var snapshot: List<ViewFactory> = emptyList()

    internal fun getFactories(): List<ViewFactory> = snapshot

    override fun getVersion(): String = BUILD_VERSION


    override fun installViewFactory(viewFactory: ViewFactory) {
        synchronized(emojiFactories) {
            if (emojiFactories.any { it == viewFactory })
                throw IllegalArgumentException("Provided factory already exists")

            emojiFactories.add(viewFactory)
            snapshot = emojiFactories.toList()
        }

        Logger.info("View factory of ${viewFactory.pluginId} added")

        rebuildViews()
    }

    override fun uninstallViewFactory(viewFactory: ViewFactory) {
        val removed = synchronized(emojiFactories) {
            emojiFactories
                .removeIf { it == viewFactory }
                .also { if (it) snapshot = emojiFactories.toList() }
        }

        if (!removed)
            return

        Logger.info("Badge factory removed")

        rebuildViews()
    }

    override fun rebindViews(params: BadgesSdk.RebindViewsParams) = runOnUIThreadNow {
        // ширина бейджа могла измениться, тогда ячейки списка диалогов надо пере-измерить
        if (EmojiRegistry.rebindAll(params.viewFactory, params.userId))
            EmojiRegistry.refreshDialogCells()
    }

    // фабрики живут ровно столько же, сколько и плагин
    internal fun clear() {
        synchronized(emojiFactories) {
            emojiFactories.clear()
            snapshot = emptyList()
        }
    }

    // список фабрик изменился - весь кеш views собирается заново
    private fun rebuildViews() = runOnUIThreadNow {
        EmojiRegistry.rebuildAll()

        // ширина эмодзи могла измениться, ячейки списка диалогов надо пере-измерить
        EmojiRegistry.refreshDialogCells()
    }
}
