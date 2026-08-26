package com.sirelon.sellsnap.features.whatsnew.data

import com.sirelon.sellsnap.features.whatsnew.data.response.ReleaseResponse
import com.sirelon.sellsnap.features.whatsnew.model.Release
import com.sirelon.sellsnap.platform.getDeviceLanguageCode
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.module.Module
import org.koin.dsl.module

internal class FirestoreReleaseNotesRepository : ReleaseNotesRepository {

    // Every navigation entry that shows What's New content constructs its own ViewModel and
    // repository lookup — without a shared cache + mutex, the prompt and the version-history
    // screen would each issue their own Firestore query on the same cold start.
    private val mutex = Mutex()
    private var cached: List<Release>? = null

    override suspend fun getReleases(): List<Release> {
        cached?.let { return it }
        return mutex.withLock {
            cached?.let { return it }
            fetchReleases().also { fetched ->
                // An empty result means "fetch failed" or "nothing published yet" — either way
                // the next launch should retry, not stay empty for the rest of the process.
                if (fetched.isNotEmpty()) cached = fetched
            }
        }
    }

    private suspend fun fetchReleases(): List<Release> = runCatching {
        val languageCode = getDeviceLanguageCode() ?: FALLBACK_LANGUAGE_CODE
        Firebase.firestore.collection(COLLECTION).get().documents
            .mapNotNull { document ->
                // One malformed document must not take the rest of the collection down.
                runCatching { document.data<ReleaseResponse>().toDomain(languageCode) }
                    .onFailure { it.printStackTrace() }
                    .getOrNull()
            }
            .sortedByDescending { it.date }
    }.onFailure { it.printStackTrace() }.getOrDefault(emptyList())

    private companion object {
        const val COLLECTION = "release-notes"
    }
}

actual val releaseNotesModule: Module = module {
    single<ReleaseNotesRepository> { FirestoreReleaseNotesRepository() }
}
