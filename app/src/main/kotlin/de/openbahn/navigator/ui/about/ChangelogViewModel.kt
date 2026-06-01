package de.openbahn.navigator.ui.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.openbahn.navigator.update.GitHubReleaseClient
import de.openbahn.navigator.update.ReleaseNote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChangelogUiState(
    val loading: Boolean = true,
    val releases: List<ReleaseNote> = emptyList(),
    val error: String? = null,
)

class ChangelogViewModel(
    private val releaseClient: GitHubReleaseClient = GitHubReleaseClient(),
) : ViewModel() {
    private val _state = MutableStateFlow(ChangelogUiState())
    val state: StateFlow<ChangelogUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { releaseClient.fetchRecentReleases() }
                .onSuccess { releases ->
                    _state.value = ChangelogUiState(loading = false, releases = releases)
                }
                .onFailure { e ->
                    _state.value = ChangelogUiState(
                        loading = false,
                        error = e.message ?: "unknown error",
                    )
                }
        }
    }
}
