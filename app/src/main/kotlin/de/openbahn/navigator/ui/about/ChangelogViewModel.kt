package de.openbahn.navigator.ui.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.openbahn.navigator.data.ChangelogRepository
import de.openbahn.navigator.update.ReleaseNote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChangelogUiState(
    val loading: Boolean = true,
    val releases: List<ReleaseNote> = emptyList(),
    val error: String? = null,
    /** Network refresh failed but older embedded/cached notes are shown. */
    val refreshWarning: String? = null,
)

class ChangelogViewModel(
    private val changelogRepository: ChangelogRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ChangelogUiState())
    val state: StateFlow<ChangelogUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val embedded = changelogRepository.loadEmbedded()
            _state.value = if (embedded.isNotEmpty()) {
                ChangelogUiState(loading = true, releases = embedded)
            } else {
                ChangelogUiState(loading = true)
            }

            val result = changelogRepository.load()
            _state.value = when {
                result.releases.isEmpty() -> ChangelogUiState(
                    loading = false,
                    releases = emptyList(),
                    error = result.error,
                )
                result.error != null -> ChangelogUiState(
                    loading = false,
                    releases = result.releases,
                    refreshWarning = result.error,
                )
                else -> ChangelogUiState(
                    loading = false,
                    releases = result.releases,
                )
            }
        }
    }
}
