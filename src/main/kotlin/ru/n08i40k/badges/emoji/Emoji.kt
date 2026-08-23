package ru.n08i40k.badges.emoji

import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import androidx.annotation.UiThread
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.DialogObject
import org.telegram.messenger.MessagesController
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable
import ru.n08i40k.badges.BadgesSdkService
import ru.n08i40k.badges.api.ViewFactory
import ru.n08i40k.badges.util.BadgesCompat
import ru.n08i40k.badges.util.Logger
import ru.n08i40k.badges.util.UserPatcher.isPatched
import ru.n08i40k.badges.util.cloneFields
import ru.n08i40k.badges.util.getAccessibleFields
import ru.n08i40k.badges.util.getAs
import ru.n08i40k.badges.util.getField
import ru.n08i40k.badges.util.runOnUIThreadNow
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import kotlin.math.max
import kotlin.math.min

class Emoji : SwapAnimatedEmojiDrawable {
    // куда клиент кладёт бейдж без нашего вмешательства
    enum class BadgeSlot {
        // в отдельный view:
        // UserCell.emojiStatus2,
        // ChatAvatarContainer.emojiStatusDrawable2,
        // ProfileActivity.badgeDrawable;
        SEPARATE,

        // в тот же view, но только если тот не занят эмодзи:
        // ProfileSearchCell,
        // StatusBadgeComponent;
        STATUS,

        // то же, что STATUS, но при занятом статусе кастомный бейдж уезжает в слот перед именем (новые версии):
        // DialogCell.botVerification,
        // ChatMessageCell.currentNameEmojiStatusDrawable;
        STATUS_OR_NAME,
    }

    // одна view, созданная фабрикой стороннего плагина
    class BadgeHolder internal constructor(
        internal val pluginId: String,
        internal val factory: ViewFactory,
        internal val view: View,
    ) {
        // последнее, что вернул bind
        internal var visible: Boolean = false

        // фабрика бросила исключение, больше её не трогаем
        internal var broken: Boolean = false

        internal var width: Int = 0
        internal var left: Int = 0
    }

    // часть drawable под курсором
    sealed interface Part {
        // оригинальный статус пользователя
        object Original : Part

        // бейдж, который в этом слоте нарисовал бы сам клиент
        object ClientBadge : Part

        // view стороннего плагина
        data class Badge(val holder: BadgeHolder) : Part
    }

    data class EjectData(
        val drawable: WeakReference<Emoji>,
        val targetObject: WeakReference<Any>,
        val targetField: Field,
        val arrayIndex: Int?,
        val nameTextView: WeakReference<SimpleTextView>? = null,
    ) {
        fun restore() {
            val drawable = this.drawable.get() ?: return
            drawable.release()

            val targetObject = this.targetObject.get() ?: return

            val pseudoOriginal = SwapAnimatedEmojiDrawable(null, 0)

            cloneFields(drawable, pseudoOriginal, EMOJI_FIELDS)

            if (arrayIndex == null) {
                targetField.set(targetObject, pseudoOriginal)
                nameTextView?.get()?.let { textView ->
                    if (RIGHT_DRAWABLE.get(textView) === drawable)
                        textView.rightDrawable = pseudoOriginal

                    if (RIGHT_DRAWABLE_2.get(textView) === drawable)
                        textView.rightDrawable2 = pseudoOriginal
                }
                return
            }

            @Suppress("UNCHECKED_CAST")
            val array = targetField.get(targetObject)!! as Array<SwapAnimatedEmojiDrawable>

            array[arrayIndex] = pseudoOriginal
        }
    }

    companion object Reflection {
        private val CLASS = SwapAnimatedEmojiDrawable::class.java

        val PARENT_VIEW = getField(CLASS, "parentView")
        val SIZE = getField(CLASS, "size")

        // SimpleTextView
        val RIGHT_DRAWABLE = getField(SimpleTextView::class.java, "rightDrawable")
        val RIGHT_DRAWABLE_2 = getField(SimpleTextView::class.java, "rightDrawable2")

        val EMOJI_FIELDS = getAccessibleFields(SwapAnimatedEmojiDrawable::class.java)

        fun encapsulate(
            obj: Any,
            field: Field,
            arrayIndex: Int?,
            peerUserId: Long,
            badgeSlot: BadgeSlot,
            simpleTextView: SimpleTextView? = null,
        ): Emoji? {
            if (arrayIndex == null) {
                val drawable = (field.get(obj) ?: return null) as? SwapAnimatedEmojiDrawable
                    ?: throw TypeCastException("Field value type isn't SwapAnimatedEmojiDrawable")

                if (drawable as? Emoji != null) {
                    drawable.setPeerUserId(peerUserId)
                    return drawable
                }

                val newDrawable = Emoji(
                    drawable,
                    peerUserId,
                    badgeSlot,
                )

                field.set(obj, newDrawable)

                EmojiRegistry.add(
                    EjectData(
                        WeakReference(newDrawable),
                        WeakReference(obj),
                        field,
                        arrayIndex,
                        simpleTextView?.let(::WeakReference)
                    )
                )
                return newDrawable
            }

            val unknownArray = field.get(obj) ?: return null

            if (!unknownArray::class.java.isArray)
                throw TypeCastException("Field value type isn't array")

            if (unknownArray::class.java.componentType != SwapAnimatedEmojiDrawable::class.java)
                throw TypeCastException("Field value type isn't SwapAnimatedEmojiDrawable[]")

            @Suppress("UNCHECKED_CAST")
            val array = unknownArray as Array<SwapAnimatedEmojiDrawable?>

            if (array.size <= arrayIndex)
                throw IndexOutOfBoundsException("SwapAnimatedEmojiDrawable[] size is below $arrayIndex")

            val drawable = array[arrayIndex] ?: return null

            if (drawable as? Emoji != null) {
                drawable.setPeerUserId(peerUserId)
                return drawable
            }

            val newDrawable = Emoji(
                drawable,
                peerUserId,
                badgeSlot,
            )
            array[arrayIndex] = newDrawable

            EmojiRegistry.add(
                EjectData(
                    WeakReference(newDrawable),
                    WeakReference(obj),
                    field,
                    arrayIndex,
                    simpleTextView?.let(::WeakReference)
                )
            )

            return newDrawable
        }
    }

    private var peerUserId: Long = 0

    private val badgeSlot: BadgeSlot

    private val size: Int

    // отступ между соседними доп. элементами
    private val gap: Int

    // views всех зарегистрированных фабрик, в порядке регистрации
    private val holders = ArrayList<BadgeHolder>(4)

    // бейдж, который в этом слоте нарисовал бы клиент, если бы мы его не заняли
    private var clientBadge: SwapAnimatedEmojiDrawable? = null
    private var clientBadgeDocumentId: Long? = null

    private var hideOriginal: Boolean = false

    // суммарная ширина всего, что мы дорисовываем справа от статуса
    private var extrasWidth: Int = 0

    private var currentAlpha: Int = 255

    constructor(
        base: SwapAnimatedEmojiDrawable,
        peerUserId: Long,
        badgeSlot: BadgeSlot,
    ) : super(
        null,
        0
    ) {
        cloneFields(base, this, EMOJI_FIELDS)
        this.badgeSlot = badgeSlot

        this.size = SIZE.getInt(this)
        this.gap = size / 5

        PARENT_VIEW.getAs<View>(this)?.let {
            EmojiRegistry.attachTouchHandler(it, this)
        }

        createHolders()

        setPeerUserId(peerUserId)
    }

    // -- фабрики --

    private fun createHolders() {
        val parentView = PARENT_VIEW.getAs<View>(this) ?: return

        for (factory in BadgesSdkService.getFactories()) {
            val view = try {
                factory.factory.create(parentView, size)
            } catch (e: Throwable) {
                Logger.fatal(
                    "Failed to create badge view of ${factory.pluginId}",
                    e,
                    preventEject = true
                )
                continue
            }

            holders.add(BadgeHolder(factory.pluginId, factory.factory, view))
        }
    }

    // фабрика владеет своей view и должна освободить её ресурсы
    private fun destroyHolders() {
        for (holder in holders) {
            if (holder.broken)
                continue

            try {
                holder.factory.destroy(holder.view)
            } catch (e: Throwable) {
                Logger.fatal(
                    "Failed to destroy badge view of ${holder.pluginId}",
                    e,
                    preventEject = true
                )
            }
        }

        holders.clear()
    }

    // полностью пересобрать кеш views: старые выбрасываются, новые создаются
    // по актуальному списку фабрик
    @UiThread
    fun rebuild() {
        destroyHolders()
        createHolders()
        refresh()
    }

    // отвязать всё, что мы дорисовывали (drawable больше не используется)
    @UiThread
    fun release() {
        destroyHolders()

        setClientBadge(null)

        hideOriginal = false
        extrasWidth = 0
    }

    private fun bindHolders(user: TLRPC.User?) {
        for (holder in holders) {
            if (user == null || holder.broken) {
                holder.visible = false
                continue
            }

            holder.visible = try {
                holder.factory.bind(holder.view, user.id)
            } catch (e: Throwable) {
                Logger.fatal(
                    "Failed to bind badge view of ${holder.pluginId}",
                    e,
                    preventEject = true
                )
                holder.broken = true
                false
            }
        }
    }

    // -- состояние --

    private fun setClientBadge(badgeDocumentId: Long?) {
        // тот же бейдж - не пересоздаём, иначе анимация будет дёргаться на каждый refresh
        if (badgeDocumentId == clientBadgeDocumentId && (badgeDocumentId == null) == (clientBadge == null))
            return

        if (badgeDocumentId == null) {
            clientBadge?.detach()
            clientBadge = null
            clientBadgeDocumentId = null
            return
        }

        val parentView = PARENT_VIEW.getAs<View>(this) ?: return

        clientBadge?.detach()
        clientBadge = SwapAnimatedEmojiDrawable(parentView, size).apply {
            set(badgeDocumentId, false)
            setParticles(true, false)
            color = Theme.getColor(Theme.key_chats_verifiedBackground)
            attach()
        }
        clientBadgeDocumentId = badgeDocumentId
    }

    private fun refreshState() = runOnUIThreadNow {
        val user = peerUserId
            .takeIf { it != 0L }
            ?.let { MessagesController.getInstance(UserConfig.selectedAccount).getUserOrChat(it) }
                as? TLRPC.User

        bindHolders(user)

        if (user == null) {
            hideOriginal = false
            setClientBadge(null)
        } else {
            val documentId = BadgesCompat.getDocumentId(user)
            val hasStatus = hasEmojiStatus(user)

            // При пустом статусе в занятом нами слоте лежит бейдж клиента или звезда
            // премиума. Бейдж скрываем и рисуем сами, чтобы он оказался после наших,
            // а звезду - только если премиум выдан плагином: настоящий премиум её
            // заслужил. Когда бейджи клиента нечитаемы, слот не трогаем вообще.
            hideOriginal = !hasStatus && BadgesCompat.isAvailable && (
                    documentId != null || user.isPatched()
                    )

            setClientBadge(getBadgeDocumentId(user, documentId, hasStatus))
        }

        measureExtras()
        syncBounds()
        invalidateSelf()
    }

    private fun hasEmojiStatus(user: TLRPC.User): Boolean =
        DialogObject.getEmojiStatusDocumentId(user.emoji_status) != 0L

    private fun getBadgeDocumentId(
        user: TLRPC.User,
        documentId: Long?,
        hasStatus: Boolean,
    ): Long? {
        if (documentId == null)
            return null

        // статус пуст, значит слот, в котором клиент нарисовал бы бейдж, занят нами (секс)
        if (!hasStatus)
            return documentId

        return when (badgeSlot) {
            // клиент рисует бейдж своим drawable рядом со статусом
            BadgeSlot.SEPARATE -> null

            // кастомный бейдж в слоте перед именем (новая версия)
            BadgeSlot.STATUS_OR_NAME -> documentId.takeIf { !BadgesCompat.hasSecondaryBadge(user) }

            // если есть прем эмодзи, клиент не рисует бейдж
            BadgeSlot.STATUS -> documentId
        }
    }

    fun getPeerUserId(): Long = peerUserId

    fun setPeerUserId(peerUserId: Long) {
        this.peerUserId = peerUserId
        refreshState()
    }

    fun refresh() = setPeerUserId(peerUserId)

    fun hasVisibleBadges(): Boolean = holders.any { it.visible }

    // перепривязать views одной фабрики; возвращает true, если ширина изменилась
    // и ячейку, в которой мы живём, надо переизмерить
    @UiThread
    fun rebindFactory(factory: ViewFactory, userId: Long?): Boolean {
        if (userId != null && peerUserId != userId)
            return false

        if (holders.none { it.factory == factory })
            return false

        val previousWidth = extrasWidth

        refresh()

        return extrasWidth != previousWidth
    }

    // -- геометрия --

    private fun measure(holder: BadgeHolder) {
        holder.view.measure(
            View.MeasureSpec.makeMeasureSpec(size * 4, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
        )

        // view без собственного onMeasure отдаёт нулевую ширину: считаем такой бейдж
        // квадратным, иначе все бейджи схлопнутся в одну точку и лягут друг на друга
        holder.width = holder.view.measuredWidth
            .takeIf { it > 0 }
            ?.coerceAtMost(size * 4)
            ?: size

        holder.view.layout(0, 0, holder.width, size)
    }

    private fun measureExtras() {
        var width = 0
        var first = true

        for (holder in holders) {
            if (!holder.visible)
                continue

            measure(holder)

            if (!first)
                width += gap

            width += holder.width
            first = false
        }

        if (clientBadge != null) {
            if (!first)
                width += gap

            width += size
        }

        extrasWidth = width
    }

    // верх квадрата size x size, по центру относительно bounds
    private fun extrasTop(): Int {
        val height = bounds.height()

        return if (height > 0) bounds.top + (height - size) / 2 else bounds.top
    }

    private fun syncBounds() {
        var x = bounds.left + if (hideOriginal) 0 else size
        var first = true

        for (holder in holders) {
            if (!holder.visible)
                continue

            if (!first)
                x += gap

            holder.left = x
            x += holder.width
            first = false
        }

        clientBadge?.let {
            if (!first)
                x += gap

            val top = extrasTop()
            it.setBounds(x, top, x + size, top + size)
        }
    }

    fun getAdditionalWidth(): Int =
        // ну типо уменьшаем размер на 1 эмодзи, если его нет
        extrasWidth - if (hideOriginal) size else 0

    fun hitTest(x: Int, y: Int): Part? {
        val padding = AndroidUtilities.dp(3f)

        val top = extrasTop()

        if (y < min(bounds.top, top) - padding || y > max(bounds.bottom, top + size) + padding)
            return null

        for (holder in holders) {
            if (!holder.visible)
                continue

            if (x >= holder.left - padding && x <= holder.left + holder.width + padding)
                return Part.Badge(holder)
        }

        clientBadge?.let {
            if (x >= it.bounds.left - padding && x <= it.bounds.right + padding)
                return Part.ClientBadge
        }

        if (!hideOriginal && x >= bounds.left - padding && x <= bounds.left + size + padding)
            return Part.Original

        return null
    }

    // события отдаём самой view, чтобы фабрика могла обработать нажатие сама
    fun dispatchBadgeTouch(part: Part.Badge, event: MotionEvent): Boolean {
        val holder = part.holder

        if (!holder.visible || holder.broken)
            return false

        val copy = MotionEvent.obtain(event)
        copy.offsetLocation(-holder.left.toFloat(), -extrasTop().toFloat())

        val handled = try {
            holder.view.dispatchTouchEvent(copy)
        } catch (e: Throwable) {
            Logger.fatal(
                "Failed to dispatch touch to badge view of ${holder.pluginId}",
                e,
                preventEject = true
            )
            holder.broken = true
            false
        } finally {
            copy.recycle()
        }

        invalidateSelf()

        return handled
    }

    // -- отрисовка --

    override fun draw(canvas: Canvas) {
        if (!hideOriginal)
            super.draw(canvas)

        val top = extrasTop()

        for (holder in holders) {
            if (!holder.visible)
                continue

            drawHolder(canvas, holder, top)
        }

        clientBadge?.draw(canvas)
    }

    private fun drawHolder(canvas: Canvas, holder: BadgeHolder, top: Int) {
        val saved = canvas.save()

        canvas.translate(holder.left.toFloat(), top.toFloat())

        // view рисуется напрямую, а не родителем, поэтому прозрачность накладываем сами
        if (currentAlpha < 255)
            canvas.saveLayerAlpha(
                0f,
                0f,
                holder.width.toFloat(),
                size.toFloat(),
                currentAlpha
            )

        try {
            holder.view.draw(canvas)
        } catch (e: Throwable) {
            Logger.fatal(
                "Failed to draw badge view of ${holder.pluginId}",
                e,
                preventEject = true
            )
            holder.broken = true
            holder.visible = false
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        super.setBounds(left, top, left + size, bottom)
        syncBounds()
    }

    override fun invalidate() {
        super.invalidate()
        clientBadge?.invalidate()
    }

    override fun invalidateSelf() {
        super.invalidateSelf()
        clientBadge?.invalidateSelf()
    }

    override fun getMinimumWidth(): Int =
        super.getMinimumWidth() + getAdditionalWidth()

    override fun getIntrinsicWidth(): Int =
        super.getIntrinsicWidth() + getAdditionalWidth()

    override fun setAlpha(alpha: Int) {
        currentAlpha = alpha
        clientBadge?.alpha = alpha
        super.setAlpha(alpha)
    }
}

// жопа)
