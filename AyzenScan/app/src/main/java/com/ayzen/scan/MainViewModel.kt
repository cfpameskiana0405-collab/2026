package com.ayzen.scan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.ayzen.scan.data.AppSettings
import com.ayzen.scan.data.ContentType
import com.ayzen.scan.data.HistoryItem
import com.ayzen.scan.data.HistoryStore
import com.ayzen.scan.data.SettingsStore
import com.ayzen.scan.data.Source
import com.ayzen.scan.logic.formatTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val store = HistoryStore(app)
    private val settingsStore = SettingsStore(app)

    private val _history = MutableStateFlow(store.load())
    val history: StateFlow<List<HistoryItem>> = _history.asStateFlow()

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private var lastDeleted: List<HistoryItem> = emptyList()

    private fun mutate(block: (List<HistoryItem>) -> List<HistoryItem>) {
        _history.value = block(_history.value)
        store.save(_history.value)
    }

    /** Creates a history item. It is stored unless it is a scan and "save scans" is turned off. */
    fun record(content: String, type: ContentType, format: String, source: Source): HistoryItem {
        val existing = _history.value.firstOrNull { it.content == content && it.source == source }
        val now = System.currentTimeMillis()
        val nextId = maxOf(now, (_history.value.maxOfOrNull { it.id } ?: 0L) + 1)
        val item = HistoryItem(
            id = nextId,
            content = content,
            type = type,
            format = format,
            timestamp = now,
            source = source,
            favorite = existing?.favorite ?: false
        )
        if (source == Source.CREATED || _settings.value.saveHistory) {
            mutate { list -> listOf(item) + list.filterNot { it.content == content && it.source == source } }
        }
        return item
    }

    fun delete(id: Long) {
        val item = _history.value.firstOrNull { it.id == id } ?: return
        lastDeleted = listOf(item)
        mutate { list -> list.filter { it.id != id } }
    }

    fun clearAll() {
        lastDeleted = _history.value
        mutate { emptyList() }
    }

    fun undoDelete() {
        val restore = lastDeleted
        if (restore.isEmpty()) return
        lastDeleted = emptyList()
        mutate { list -> (list + restore).distinctBy { it.id }.sortedByDescending { it.timestamp } }
    }

    fun toggleFavorite(id: Long) {
        mutate { list -> list.map { if (it.id == id) it.copy(favorite = !it.favorite) else it } }
    }

    fun exportText(): String = _history.value.joinToString("\n\n") {
        "[${formatTime(it.timestamp)}] ${it.format} (${it.type.name})\n${it.content}"
    }

    fun updateSettings(block: (AppSettings) -> AppSettings) {
        _settings.value = block(_settings.value)
        settingsStore.save(_settings.value)
    }
}
