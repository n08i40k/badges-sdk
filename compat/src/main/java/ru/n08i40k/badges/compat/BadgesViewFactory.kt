package ru.n08i40k.badges.compat

import android.view.View

interface BadgesViewFactory {
    // parent - view клиента, в которую рисуется бейдж
    // возвращённая view никогда не попадает в иерархию, поэтому родителем для drawable нужно брать именно parent
    fun create(parent: View, heightPx: Int): View

    fun bind(view: View, userId: Long): Boolean

    // SDK выбросил view, надо отвязать всё, что к ней привязано
    fun destroy(view: View) {}
}
