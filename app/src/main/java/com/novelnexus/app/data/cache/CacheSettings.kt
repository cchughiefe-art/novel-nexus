package com.novelnexus.app.data.cache

import android.content.Context

class CacheSettings(context: Context) {
    private val prefs = context.getSharedPreferences("novel_nexus_cache", Context.MODE_PRIVATE)
    fun limitMb(): Int = prefs.getInt("limit_mb", 100).coerceIn(25, 250)
    fun setLimitMb(value: Int) {
        prefs.edit().putInt("limit_mb", value.coerceIn(25, 250)).apply()
    }
}
