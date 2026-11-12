package com.ayzen.scan.data

enum class ContentType {
    URL, TEXT, WIFI, EMAIL, PHONE, SMS, GEO, CONTACT, EVENT, PRODUCT, BOOK
}

enum class Source { SCANNED, CREATED }

data class HistoryItem(
    val id: Long,
    val content: String,
    val type: ContentType,
    val format: String,
    val timestamp: Long,
    val source: Source,
    val favorite: Boolean = false
)

data class AppSettings(
    val vibrate: Boolean = true,
    val beep: Boolean = true,
    val saveHistory: Boolean = true,
    val copyOnScan: Boolean = false,
    /** 0 = follow system, 1 = light, 2 = dark */
    val themeMode: Int = 2
)
