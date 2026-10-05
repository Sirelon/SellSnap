package com.sirelon.sellsnap.features.announcements.data

import com.sirelon.sellsnap.features.announcements.data.response.AnnouncementResponse
import com.sirelon.sellsnap.features.announcements.model.Announcement
import com.sirelon.sellsnap.features.whatsnew.data.FALLBACK_LANGUAGE_CODE
import com.sirelon.sellsnap.platform.getDeviceLanguageCode
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.installations.installations
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Reads use Firestore's default source: the server when online, the local cache when offline. An
 * offline launch can therefore still show an announcement that was deactivated after this device
 * last synced. That is accepted - the alternative is no announcement for anyone offline.
 *
 * A document whose `minVersion`/`maxVersion` was typed as a number in the console fails to decode
 * and is skipped with the other malformed documents; see AnnouncementRulesResponse.
 */
internal class FirestoreAnnouncementsRepository : AnnouncementsRepository {

    // The ViewModel is the only caller, but a cache + mutex keeps a second caller from issuing a
    // second query on the same cold start.
    private val mutex = Mutex()
    private val installationIdMutex = Mutex()
    private var cached: List<Announcement>? = null
    private var installationId: String? = null

    override suspend fun getAnnouncements(): List<Announcement> {
        cached?.let { return it }
        return mutex.withLock {
            cached?.let { return it }
            fetchAnnouncements().also { fetched ->
                // An empty result means "fetch failed" or "nothing active" - either way the next
                // launch should retry, not stay empty for the rest of the process.
                if (fetched.isNotEmpty()) cached = fetched
            }
        }
    }

    override suspend fun getInstallationId(): String? {
        installationId?.let { return it }
        return installationIdMutex.withLock {
            installationId ?: runCatching { Firebase.installations.getId() }
                .onFailure { it.printStackTrace() }
                .getOrNull()
                ?.also {
                    installationId = it
                    // Printed unconditionally: there is no debug-build flag reachable from
                    // mobileMain. The ID is a random per-install value, and the owner copies it
                    // from the log into an announcement's `userIds` to target one device.
                    println("SellSnap FID: $it")
                }
        }
    }

    private suspend fun fetchAnnouncements(): List<Announcement> = runCatching {
        val languageCode = getDeviceLanguageCode() ?: FALLBACK_LANGUAGE_CODE
        Firebase.firestore.collection(COLLECTION).where { "active" equalTo true }.get().documents
            .mapNotNull { document ->
                // One malformed document must not take the rest of the collection down.
                runCatching { document.data<AnnouncementResponse>().toDomain(document.id, languageCode) }
                    .onFailure { it.printStackTrace() }
                    .getOrNull()
            }
    }.onFailure { it.printStackTrace() }.getOrDefault(emptyList())

    private companion object {
        const val COLLECTION = "announcements"
    }
}

actual val announcementsDataModule: Module = module {
    single<AnnouncementsRepository> { FirestoreAnnouncementsRepository() }
}
