package com.novelnexus.app.ui.reader

object ReaderKeyRouter {
    var enabled: Boolean = false
    var onUp: (() -> Unit)? = null
    var onDown: (() -> Unit)? = null

    fun clear() {
        enabled = false
        onUp = null
        onDown = null
    }
}
