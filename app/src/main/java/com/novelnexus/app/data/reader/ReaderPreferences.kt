package com.novelnexus.app.data.reader

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

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
    val autoScroll: Boolean = false,
    val autoScrollSpeed: Float = 45f,
    val autoNext: Boolean = false,
    val autoNextSeconds: Int = 5,
    val continuousMode: Boolean = false,
    val orientation: String = "AUTO",
    val reducedMotion: Boolean = false,
    val highContrast: Boolean = false,
    val largeControls: Boolean = false,
    val tapLeftAction: String = "PAGE",
    val tapRightAction: String = "PAGE",
    val swipeChapter: Boolean = false
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
        val AUTO_SCROLL = booleanPreferencesKey("auto_scroll")
        val AUTO_SCROLL_SPEED = floatPreferencesKey("auto_scroll_speed")
        val AUTO_NEXT = booleanPreferencesKey("auto_next")
        val AUTO_NEXT_SECONDS = intPreferencesKey("auto_next_seconds")
        val CONTINUOUS = booleanPreferencesKey("continuous_mode")
        val ORIENTATION = stringPreferencesKey("orientation")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val LARGE_CONTROLS = booleanPreferencesKey("large_controls")
        val TAP_LEFT = stringPreferencesKey("tap_left_action")
        val TAP_RIGHT = stringPreferencesKey("tap_right_action")
        val SWIPE_CHAPTER = booleanPreferencesKey("swipe_chapter")
    }

    private val bookStore = context.getSharedPreferences("reader_book_preferences", Context.MODE_PRIVATE)
    private val bookFlows = ConcurrentHashMap<String, MutableStateFlow<ReaderPrefs?>>()

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
            autoScroll = p[Keys.AUTO_SCROLL] ?: false,
            autoScrollSpeed = p[Keys.AUTO_SCROLL_SPEED] ?: 45f,
            autoNext = p[Keys.AUTO_NEXT] ?: false,
            autoNextSeconds = p[Keys.AUTO_NEXT_SECONDS] ?: 5,
            continuousMode = p[Keys.CONTINUOUS] ?: false,
            orientation = p[Keys.ORIENTATION] ?: "AUTO",
            reducedMotion = p[Keys.REDUCED_MOTION] ?: false,
            highContrast = p[Keys.HIGH_CONTRAST] ?: false,
            largeControls = p[Keys.LARGE_CONTROLS] ?: false,
            tapLeftAction = p[Keys.TAP_LEFT] ?: "PAGE",
            tapRightAction = p[Keys.TAP_RIGHT] ?: "PAGE",
            swipeChapter = p[Keys.SWIPE_CHAPTER] ?: false
        )
    }

    fun effectiveFlow(novelUrl: String): Flow<ReaderPrefs> =
        combine(flow, bookFlow(novelUrl)) { global, book -> book ?: global }

    fun hasBookOverride(novelUrl: String): Boolean =
        bookStore.getBoolean("${bookId(novelUrl)}_enabled", false)

    suspend fun setBookOverride(novelUrl: String, enabled: Boolean) {
        val id = bookId(novelUrl)
        if (enabled) {
            val current = flow.first()
            saveBook(id, current)
            bookStore.edit().putBoolean("${id}_enabled", true).apply()
            bookFlow(novelUrl).value = current
        } else {
            bookStore.edit().putBoolean("${id}_enabled", false).apply()
            bookFlow(novelUrl).value = null
        }
    }

    suspend fun updateForBook(novelUrl: String, block: (ReaderPrefs) -> ReaderPrefs) {
        if (!hasBookOverride(novelUrl)) {
            update(block)
            return
        }
        val id = bookId(novelUrl)
        val current = bookFlow(novelUrl).value ?: flow.first()
        val next = block(current)
        saveBook(id, next)
        bookFlow(novelUrl).value = next
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
            p[Keys.AUTO_SCROLL] = next.autoScroll
            p[Keys.AUTO_SCROLL_SPEED] = next.autoScrollSpeed
            p[Keys.AUTO_NEXT] = next.autoNext
            p[Keys.AUTO_NEXT_SECONDS] = next.autoNextSeconds
            p[Keys.CONTINUOUS] = next.continuousMode
            p[Keys.ORIENTATION] = next.orientation
            p[Keys.REDUCED_MOTION] = next.reducedMotion
            p[Keys.HIGH_CONTRAST] = next.highContrast
            p[Keys.LARGE_CONTROLS] = next.largeControls
            p[Keys.TAP_LEFT] = next.tapLeftAction
            p[Keys.TAP_RIGHT] = next.tapRightAction
            p[Keys.SWIPE_CHAPTER] = next.swipeChapter
        }
    }

    private fun bookFlow(novelUrl: String): MutableStateFlow<ReaderPrefs?> {
        val id = bookId(novelUrl)
        return bookFlows.getOrPut(id) { MutableStateFlow(loadBook(id)) }
    }

    private fun bookId(url: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray())
            .take(10)
            .joinToString("") { "%02x".format(it) }

    private fun loadBook(id: String): ReaderPrefs? {
        if (!bookStore.getBoolean("${id}_enabled", false)) return null
        return ReaderPrefs(
            theme = bookStore.getString("${id}_theme", "AMOLED") ?: "AMOLED",
            mode = bookStore.getString("${id}_mode", "SCROLL") ?: "SCROLL",
            fontSize = bookStore.getFloat("${id}_fontSize", 19f),
            lineHeight = bookStore.getFloat("${id}_lineHeight", 1.70f),
            paragraphSpacing = bookStore.getFloat("${id}_paragraphSpacing", 9f),
            margin = bookStore.getFloat("${id}_margin", 22f),
            brightness = bookStore.getFloat("${id}_brightness", -1f),
            immersive = bookStore.getBoolean("${id}_immersive", true),
            keepAwake = bookStore.getBoolean("${id}_keepAwake", false),
            volumeNavigation = bookStore.getBoolean("${id}_volumeNavigation", false),
            autoScroll = bookStore.getBoolean("${id}_autoScroll", false),
            autoScrollSpeed = bookStore.getFloat("${id}_autoScrollSpeed", 45f),
            autoNext = bookStore.getBoolean("${id}_autoNext", false),
            autoNextSeconds = bookStore.getInt("${id}_autoNextSeconds", 5),
            continuousMode = bookStore.getBoolean("${id}_continuousMode", false),
            orientation = bookStore.getString("${id}_orientation", "AUTO") ?: "AUTO",
            reducedMotion = bookStore.getBoolean("${id}_reducedMotion", false),
            highContrast = bookStore.getBoolean("${id}_highContrast", false),
            largeControls = bookStore.getBoolean("${id}_largeControls", false),
            tapLeftAction = bookStore.getString("${id}_tapLeftAction", "PAGE") ?: "PAGE",
            tapRightAction = bookStore.getString("${id}_tapRightAction", "PAGE") ?: "PAGE",
            swipeChapter = bookStore.getBoolean("${id}_swipeChapter", false)
        )
    }

    private fun saveBook(id: String, p: ReaderPrefs) {
        bookStore.edit()
            .putString("${id}_theme", p.theme)
            .putString("${id}_mode", p.mode)
            .putFloat("${id}_fontSize", p.fontSize)
            .putFloat("${id}_lineHeight", p.lineHeight)
            .putFloat("${id}_paragraphSpacing", p.paragraphSpacing)
            .putFloat("${id}_margin", p.margin)
            .putFloat("${id}_brightness", p.brightness)
            .putBoolean("${id}_immersive", p.immersive)
            .putBoolean("${id}_keepAwake", p.keepAwake)
            .putBoolean("${id}_volumeNavigation", p.volumeNavigation)
            .putBoolean("${id}_autoScroll", p.autoScroll)
            .putFloat("${id}_autoScrollSpeed", p.autoScrollSpeed)
            .putBoolean("${id}_autoNext", p.autoNext)
            .putInt("${id}_autoNextSeconds", p.autoNextSeconds)
            .putBoolean("${id}_continuousMode", p.continuousMode)
            .putString("${id}_orientation", p.orientation)
            .putBoolean("${id}_reducedMotion", p.reducedMotion)
            .putBoolean("${id}_highContrast", p.highContrast)
            .putBoolean("${id}_largeControls", p.largeControls)
            .putString("${id}_tapLeftAction", p.tapLeftAction)
            .putString("${id}_tapRightAction", p.tapRightAction)
            .putBoolean("${id}_swipeChapter", p.swipeChapter)
            .apply()
    }
}
