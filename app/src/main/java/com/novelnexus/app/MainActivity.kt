package com.novelnexus.app

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.novelnexus.app.ui.navigation.NovelNexusRoot
import com.novelnexus.app.ui.reader.ReaderKeyRouter
import com.novelnexus.app.ui.theme.NovelNexusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NovelNexusTheme {
                NovelNexusRoot((application as NovelNexusApp).graph)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (ReaderKeyRouter.enabled && event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    ReaderKeyRouter.onUp?.invoke()
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    ReaderKeyRouter.onDown?.invoke()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
