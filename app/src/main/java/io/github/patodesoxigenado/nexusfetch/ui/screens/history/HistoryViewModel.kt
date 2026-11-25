package io.github.patodesoxigenado.nexusfetch.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.patodesoxigenado.nexusfetch.data.local.db.HistoryDao
import io.github.patodesoxigenado.nexusfetch.domain.model.HistoryEntry
import io.github.patodesoxigenado.nexusfetch.domain.model.JobModule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyDao: HistoryDao,
) : ViewModel() {

    private val _selectedModule = MutableStateFlow<JobModule?>(null)
    val selectedModule: StateFlow<JobModule?> = _selectedModule.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: StateFlow<List<HistoryEntry>> = _selectedModule
        .flatMapLatest { mod ->
            if (mod == null) {
                historyDao.observeAll()
            } else {
                historyDao.observeByModule(mod.id)
            }
        }
        .map { list -> list.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectFilter(module: JobModule?) {
        _selectedModule.value = module
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch { historyDao.deleteById(id) }
    }

    fun clearAll() {
        viewModelScope.launch { historyDao.clearAll() }
    }
}
