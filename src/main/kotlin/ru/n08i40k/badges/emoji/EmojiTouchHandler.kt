package ru.n08i40k.badges.emoji

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import org.telegram.ui.ActionBar.SimpleTextView
import ru.n08i40k.badges.util.Logger
import ru.n08i40k.badges.util.getAs
import ru.n08i40k.badges.util.getField
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

class EmojiTouchHandler private constructor(
    private val previous: View.OnTouchListener?,
) : View.OnTouchListener {
    companion object {
        // SimpleTextView
        private val RIGHT_DRAWABLE_ON_CLICK_LISTENER =
            getField(SimpleTextView::class.java, "rightDrawableOnClickListener")

        @SuppressLint("DiscouragedPrivateApi", "PrivateApi")
        private val PREVIOUS_LISTENER: (View) -> View.OnTouchListener? = run {
            val listenerInfo = try {
                View::class.java.getDeclaredField("mListenerInfo")
                    .apply { isAccessible = true }
            } catch (_: Throwable) {
                Logger.info("View.mListenerInfo is inaccessible")
                return@run { null }
            }

            val onTouchListener = try {
                Class.forName($$"android.view.View$ListenerInfo")
                    .getDeclaredField("mOnTouchListener")
                    .apply { isAccessible = true }
            } catch (_: Throwable) {
                Logger.info("ListenerInfo.mOnTouchListener is inaccessible")
                return@run { null }
            }

            return@run { view ->
                try {
                    listenerInfo.get(view)?.let(onTouchListener::get) as? View.OnTouchListener
                } catch (_: Throwable) {
                    null
                }
            }
        }

        fun install(view: View): EmojiTouchHandler {
            val previous = PREVIOUS_LISTENER(view)

            if (previous is EmojiTouchHandler)
                return previous

            return EmojiTouchHandler(previous)
                .also(view::setOnTouchListener)
        }
    }

    private val drawables = CopyOnWriteArrayList<WeakReference<Emoji>>()

    private var pressedDrawable: Emoji? = null
    private var pressedPart: Emoji.Part? = null

    fun register(drawable: Emoji) {
        val it = drawables.iterator()

        while (it.hasNext()) {
            val known = it.next().get()

            if (known === drawable)
                return
        }

        drawables.removeIf { it.get() == null }
        drawables.add(WeakReference(drawable))
    }

    fun restore(view: View) {
        drawables.clear()
        reset()
        view.setOnTouchListener(previous)
    }

    private fun hitTest(x: Int, y: Int): Pair<Emoji, Emoji.Part>? {
        val it = drawables.iterator()

        while (it.hasNext()) {
            val drawable = it.next().get() ?: continue
            val part = drawable.hitTest(x, y) ?: continue

            return drawable to part
        }

        return null
    }

    private fun getOriginalClickListener(view: View): View.OnClickListener? {
        if (view !is SimpleTextView)
            return null

        return RIGHT_DRAWABLE_ON_CLICK_LISTENER.getAs<View.OnClickListener>(view)
    }

    private fun reset() {
        pressedDrawable = null
        pressedPart = null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View, event: MotionEvent): Boolean {
        val x = event.x.toInt()
        val y = event.y.toInt()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                reset()

                val (drawable, part) = hitTest(x, y)
                    ?: return previous?.onTouch(view, event) ?: false

                when (part) {
                    // клиентский бейдж обрабатывает сам клиент
                    Emoji.Part.ClientBadge ->
                        return previous?.onTouch(view, event) ?: false

                    Emoji.Part.Original ->
                        if (getOriginalClickListener(view) == null)
                            return previous?.onTouch(view, event) ?: false

                    // если view фабрики не заинтересована в жесте, не перехватываем его
                    is Emoji.Part.Badge ->
                        if (!drawable.dispatchBadgeTouch(part, event))
                            return previous?.onTouch(view, event) ?: false
                }

                pressedDrawable = drawable
                pressedPart = part

                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val drawable = pressedDrawable
                val part = pressedPart

                if (drawable == null || part == null)
                    return previous?.onTouch(view, event) ?: false

                // view сама решает, считать ли жест отменённым при выходе за свои границы
                if (part is Emoji.Part.Badge) {
                    drawable.dispatchBadgeTouch(part, event)
                    return true
                }

                if (drawable.hitTest(x, y) != part)
                    reset()

                return true
            }

            MotionEvent.ACTION_UP -> {
                val drawable = pressedDrawable
                val part = pressedPart

                if (drawable == null || part == null)
                    return previous?.onTouch(view, event) ?: false

                reset()

                Logger.tryOrFatal("handle badge emoji click") {
                    when (part) {
                        is Emoji.Part.Badge -> {
                            drawable.dispatchBadgeTouch(part, event)
                        }

                        Emoji.Part.Original -> {
                            getOriginalClickListener(view)?.onClick(view)
                        }

                        Emoji.Part.ClientBadge -> Unit
                    }
                }

                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                val drawable = pressedDrawable
                val part = pressedPart

                if (drawable == null || part == null)
                    return previous?.onTouch(view, event) ?: false

                if (part is Emoji.Part.Badge)
                    drawable.dispatchBadgeTouch(part, event)

                reset()

                return true
            }
        }

        return previous?.onTouch(view, event) ?: false
    }
}
