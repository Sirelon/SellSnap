package com.sirelon.sellsnap.features.whatsnew.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sirelon.sellsnap.config.AppConfig
import com.sirelon.sellsnap.features.whatsnew.data.ReleaseNotesRepository
import com.sirelon.sellsnap.features.whatsnew.data.WhatsNewStore
import com.sirelon.sellsnap.features.whatsnew.model.Release
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class WhatsNewUiState(
    val releases: List<Release> = emptyList(),
    val currentRelease: Release? = null,
    val isLoading: Boolean = true,
)

class WhatsNewViewModel(
    private val releaseNotesRepository: ReleaseNotesRepository,
    private val whatsNewStore: WhatsNewStore,
) : ViewModel() {

    private val _state = MutableStateFlow(WhatsNewUiState())
    val state: StateFlow<WhatsNewUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val releases = releaseNotesRepository.getReleases()
            _state.update {
                it.copy(
                    releases = releases,
                    currentRelease = releases.firstOrNull { release -> release.version == AppConfig.appVersionName },
                    isLoading = false,
                )
            }
        }
    }

    /**
     * Suspends until releases have loaded (bounded — a slow network must not hold a dialog
     * over content the user already started reading) then reports whether the running
     * version has unseen, published release notes. On timeout this returns false for *this*
     * launch only and leaves the seen-marker untouched, so the next launch (served from
     * Firestore's local cache) gets its chance.
     */
    suspend fun shouldShowDialog(): Boolean {
        val loaded = withTimeoutOrNull(DIALOG_LOAD_TIMEOUT_MS) {
            state.first { !it.isLoading }
        } ?: return false
        return loaded.currentRelease != null &&
            whatsNewStore.lastSeenVersion() != AppConfig.appVersionName
    }

    fun markSeen() {
        viewModelScope.launch {
            whatsNewStore.markVersionSeen(AppConfig.appVersionName)
        }
    }

    private companion object {
        const val DIALOG_LOAD_TIMEOUT_MS = 3_000L
    }
}
