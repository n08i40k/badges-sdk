package ru.n08i40k.badges

import ru.n08i40k.badges.api.BadgesSdk
import ru.n08i40k.badges.api.ViewFactory
import ru.n08i40k.badges.emoji.EmojiRegistry
import ru.n08i40k.badges.util.Logger
import ru.n08i40k.badges.util.runOnUIThreadNow

object BadgesSdkService : BadgesSdk {
    data class EmojiFactory(
        val pluginId: String,
        val factory: ViewFactory
    )

    private val emojiFactories = ArrayList<EmojiFactory>(16)

    // неизменяемый снимок для чтения из drawable без блокировок
    @Volatile
    private var snapshot: List<EmojiFactory> = emptyList()

    fun getFactories(): List<EmojiFactory> = snapshot

    override fun getVersion(): String = Plugin.getVersion()!!

    override fun addBadgeFactory(
        pluginId: String,
        factory: ViewFactory
    ) {
        synchronized(emojiFactories) {
            if (emojiFactories.any { it.pluginId == pluginId && it.factory == factory })
                throw IllegalArgumentException("Provided factory already exists")

            emojiFactories.add(EmojiFactory(pluginId, factory))
            snapshot = emojiFactories.toList()
        }

        Logger.info("Badge factory of $pluginId added")

        rebuildViews()
    }

    override fun removeBadgeFactory(factory: ViewFactory) {
        val removed = synchronized(emojiFactories) {
            emojiFactories
                .removeIf { it.factory == factory }
                .also { if (it) snapshot = emojiFactories.toList() }
        }

        if (!removed)
            return

        Logger.info("Badge factory removed")

        rebuildViews()
    }

    override fun scheduleRebind(factory: ViewFactory) = rebind(factory, null)

    override fun scheduleRebind(factory: ViewFactory, userId: Long) = rebind(factory, userId)

    private fun rebind(factory: ViewFactory, userId: Long?) = runOnUIThreadNow {
        // ширина бейджа могла измениться, тогда ячейки списка диалогов надо переизмерить
        if (EmojiRegistry.rebindAll(factory, userId))
            EmojiRegistry.refreshDialogCells()
    }

    // надо выгрузить, если автор это не сделал вручную
    fun onPluginUnload(id: String) {
        val removed = synchronized(emojiFactories) {
            emojiFactories
                .removeIf { it.pluginId == id }
                .also { if (it) snapshot = emojiFactories.toList() }
        }

        if (!removed)
            return

        Logger.info("Badge factories for plugin '$id' removed")

        rebuildViews()
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

        // ширина эмодзи могла измениться, ячейки списка диалогов надо переизмерить
        EmojiRegistry.refreshDialogCells()
    }
}
