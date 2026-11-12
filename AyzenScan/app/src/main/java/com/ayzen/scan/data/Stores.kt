package com.ayzen.scan.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Stores the history list as JSON inside SharedPreferences (no extra libraries needed). */
class HistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("ayzen_history", Context.MODE_PRIVATE)

    fun load(): List<HistoryItem> {
        val raw = prefs.getString("items", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = ArrayList<HistoryItem>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    HistoryItem(
                        id = o.getLong("id"),
                        content = o.getString("content"),
                        type = runCatching { ContentType.valueOf(o.getString("type")) }
                            .getOrDefault(ContentType.TEXT),
                        format = o.optString("format", ""),
                        timestamp = o.getLong("ts"),
                        source = runCatching { Source.valueOf(o.getString("source")) }
                            .getOrDefault(Source.SCANNED),
                        favorite = o.optBoolean("fav", false)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(items: List<HistoryItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("content", item.content)
                    .put("type", item.type.name)
                    .put("format", item.format)
                    .put("ts", item.timestamp)
                    .put("source", item.source.name)
                    .put("fav", item.favorite)
            )
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("ayzen_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        vibrate = prefs.getBoolean("vibrate", true),
        beep = prefs.getBoolean("beep", true),
        saveHistory = prefs.getBoolean("save_history", true),
        copyOnScan = prefs.getBoolean("copy_on_scan", false),
        themeMode = prefs.getInt("theme", 2)
    )

    fun save(s: AppSettings) {
        prefs.edit()
            .putBoolean("vibrate", s.vibrate)
            .putBoolean("beep", s.beep)
            .putBoolean("save_history", s.saveHistory)
            .putBoolean("copy_on_scan", s.copyOnScan)
            .putInt("theme", s.themeMode)
            .apply()
    }
}
