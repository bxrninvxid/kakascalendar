package com.example.kakascalendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainVm(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()

    /** null = still loading */
    val entries: StateFlow<List<Entry>?> =
        dao.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun add(type: Int, comment: String, tags: List<String>, time: Long) = viewModelScope.launch {
        dao.insert(Entry(timestamp = time, type = type, comment = comment.trim(), tags = tags.joinToString(",")))
    }

    fun delete(entry: Entry) = viewModelScope.launch { dao.delete(entry) }
    fun restore(entry: Entry) = viewModelScope.launch { dao.insert(entry) }
}
