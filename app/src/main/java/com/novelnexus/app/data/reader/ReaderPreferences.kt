package com.novelnexus.app.data.reader

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

private val Context.readerDataStore by preferencesDataStore(name = "reader_preferences")

data class ReaderPrefs(
    val theme: String = "AMOLED",
    val mode: String = "SCROLL",
    val fontSize: Float = 19f,
    val lineHeight: Float = 1.70f,
    val paragraphSpacing: Float = 9f,
    val margin: Float = 22f,
    val brightness: Float = -1f,
    val immersive: Boolean = true,
    val keepAwake: Boolean = false,
    val volumeNavigation: Boolean = false,
    val autoNext: Boolean = false,
    val orientation: String = "AUTO"
)

class ReaderPreferences(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val MODE = stringPreferencesKey("mode")
        val FONT = floatPreferencesKey("font")
        val LINE = floatPreferencesKey("line")
        val PARAGRAPH = floatPreferencesKey("paragraph")
        val MARGIN = floatPreferencesKey("margin")
        val BRIGHTNESS = floatPreferencesKey("brightness")
        val IMMERSIVE = booleanPreferencesKey("immersive")
        val KEEP_AWAKE = booleanPreferencesKey("keep_awake")
        val VOLUME_NAV = booleanPreferencesKey("volume_nav")
        val AUTO_NEXT = booleanPreferencesKey("auto_next")
        val ORIENTATION = stringPreferencesKey("orientation")
    }

    val flow: Flow<ReaderPrefs> = context.readerDataStore.data.map { p ->
        ReaderPrefs(
            theme = p[Keys.THEME] ?: "AMOLED",
            mode = p[Keys.MODE] ?: "SCROLL",
            fontSize = p[Keys.FONT] ?: 19f,
            lineHeight = p[Keys.LINE] ?: 1.70f,
            paragraphSpacing = p[Keys.PARAGRAPH] ?: 9f,
            margin = p[Keys.MARGIN] ?: 22f,
            brightness = p[Keys.BRIGHTNESS] ?: -1f,
            immersive = p[Keys.IMMERSIVE] ?: true,
            keepAwake = p[Keys.KEEP_AWAKE] ?: false,
            volumeNavigation = p[Keys.VOLUME_NAV] ?: false,
            autoNext = p[Keys.AUTO_NEXT] ?: false,
            orientation = p[Keys.ORIENTATION] ?: "AUTO"
        )
    }

    suspend fun update(block: (ReaderPrefs) -> ReaderPrefs) {
        val current = flow.first()
        val next = block(current)
        context.readerDataStore.edit { p ->
            p[Keys.THEME] = next.theme
            p[Keys.MODE] = next.mode
            p[Keys.FONT] = next.fontSize
            p[Keys.LINE] = next.lineHeight
            p[Keys.PARAGRAPH] = next.paragraphSpacing
            p[Keys.MARGIN] = next.margin
            p[Keys.BRIGHTNESS] = next.brightness
            p[Keys.IMMERSIVE] = next.immersive
            p[Keys.KEEP_AWAKE] = next.keepAwake
            p[Keys.VOLUME_NAV] = next.volumeNavigation
            p[Keys.AUTO_NEXT] = next.autoNext
            p[Keys.ORIENTATION] = next.orientation
        }
    }
}
