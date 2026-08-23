package ru.n08i40k.badges.api

import android.view.View

interface ViewFactory {
    fun create(parent: View, heightPx: Int): View

    fun bind(view: View, userId: Long): Boolean

    fun destroy(view: View)
}
