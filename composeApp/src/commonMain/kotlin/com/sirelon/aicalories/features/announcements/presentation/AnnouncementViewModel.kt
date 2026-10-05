package com.sirelon.sellsnap.features.announcements.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sirelon.sellsnap.config.AppConfig
import com.sirelon.sellsnap.features.announcements.data.AnnouncementsRepository
import com.sirelon.sellsnap.features.announcements.data.AnnouncementsStore
import com.sirelon.sellsnap.features.announcements.data.selectAnnouncement
import com.sirelon.sellsnap.features.announcements.model.Announcement
import com.sirelon.sellsnap.features.review.ReviewPromptCoordinator
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class AnnouncementUiState(
    val announcement: Announcement? = null,
)

internal class AnnouncementViewModel(
    private val repository: AnnouncementsRepository,
    private val store: AnnouncementsStore,
    private val olxAccountStore: OlxAccountStore,
    private val reviewPromptCoordinator: ReviewPromptCoordinator,
) : ViewModel() {

    private class Loaded(val announcements: List<Announcement>, val installationId: String?)

    private val loaded = CompletableDeferred<Loaded>()
    private var shownThisProcess = false

    private val _state = MutableStateFlow(AnnouncementUiState())
    val state: StateFlow<AnnouncementUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // The installation ID is requested here too, not only on demand, so the repository
            // logs it at startup for the owner to copy into an announcement's `userIds`. Both
            // load in parallel so they share the one LOAD_TIMEOUT_MS budget.
            val installationId = async { repository.getInstallationId() }
            val announcements = async { repository.getAnnouncements() }
            loaded.complete(Loaded(announcements.await(), installationId.await()))
        }
    }

    /**
     * Suspends until announcements have loaded (bounded - a slow network must not hold a dialog
     * over content the user already started reading), picks the one to show, and when there is
     * one publishes it as [state] and returns true. On timeout this returns false for *this*
     * launch only and records nothing, so the next launch (served from Firestore's local cache)
     * gets its chance.
     *
     * Showing counts as seen: a `once` announcement is recorded here rather than on dismissal, so
     * it cannot come back after the process dies while it is on screen. At most one announcement
     * is shown per process.
     */
    suspend fun shouldShow(): Boolean {
        if (shownThisProcess) return false
        val data = withTimeoutOrNull(LOAD_TIMEOUT_MS) { loaded.await() } ?: return false
        // Storage failures read as "nothing seen / nothing on this device": an announcement is
        // never worth a crash inside a LaunchedEffect.
        val seenIds = runCatching {
            data.announcements.filter { store.isSeen(it.id) }.map { it.id }.toSet()
        }.getOrDefault(emptySet())
        val olxUserIds = runCatching {
            olxAccountStore.readRaw()?.accounts.orEmpty().mapNotNull { it.olxUserId }.toSet()
        }.getOrDefault(emptySet())
        val announcement = selectAnnouncement(
            announcements = data.announcements,
            appVersion = AppConfig.appVersionName,
            installationId = data.installationId,
            olxUserIds = olxUserIds,
            seenIds = seenIds,
        ) ?: return false

        shownThisProcess = true
        if (announcement.showMode == Announcement.ShowMode.Once) {
            runCatching { store.markSeen(announcement.id) }
        }
        // One uninvited interruption per session: a seller who has already been handed an
        // announcement on launch is not also asked to rate the app. See ReviewPromptGate.
        reviewPromptCoordinator.announcementShownThisSession = true
        _state.update { it.copy(announcement = announcement) }
        return true
    }

    fun dismiss() {
        _state.update { it.copy(announcement = null) }
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 3_000L
    }
}
