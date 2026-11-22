package com.sirelon.sellsnap.features.seller.auth.data

import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.analytics.AnalyticsEvents
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.module.Module
import org.koin.dsl.module

/** Reads `config/olx_login.systemBrowser` once per process; see [OlxLoginModeRepository]. */
internal class FirestoreOlxLoginModeRepository(
    private val analytics: Analytics,
) : OlxLoginModeRepository {
    private val mutex = Mutex()
    private var cached: Boolean? = null

    override suspend fun useSystemBrowser(): Boolean = mutex.withLock {
        cached ?: fetch().also { systemBrowser ->
            cached = systemBrowser
            analytics.setUserProperty(
                AnalyticsEvents.USER_PROPERTY_OLX_LOGIN_MODE,
                if (systemBrowser) "system_browser" else "in_app",
            )
        }
    }

    private suspend fun fetch(): Boolean = runCatching {
        val document = Firebase.firestore.collection(COLLECTION).document(DOCUMENT).get()
        document.exists && document.get<Boolean?>(FIELD) == true
    }.onFailure { it.printStackTrace() }.getOrDefault(false)

    private companion object {
        const val COLLECTION = "config"
        const val DOCUMENT = "olx_login"
        const val FIELD = "systemBrowser"
    }
}

actual val olxLoginModeModule: Module = module {
    single<OlxLoginModeRepository> { FirestoreOlxLoginModeRepository(analytics = get()) }
}
