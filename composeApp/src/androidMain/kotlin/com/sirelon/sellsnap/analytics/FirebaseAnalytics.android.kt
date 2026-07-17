package com.sirelon.sellsnap.analytics

import android.content.Context
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.analytics
import dev.gitlive.firebase.crashlytics.crashlytics
import com.google.firebase.FirebaseApp
import org.koin.core.module.Module
import org.koin.dsl.module

internal class FirebaseAnalyticsImpl(
    context: Context,
) : Analytics {
    init {
        // Initialize if configuration is present; otherwise keep analytics as no-op.
        runCatching { FirebaseApp.initializeApp(context) }
    }

    private inline fun withAnalytics(block: () -> Unit) {
        runCatching { block() }
    }

    private inline fun withCrashlytics(block: () -> Unit) {
        runCatching { block() }
    }

    override fun logEvent(name: String, params: Map<String, Any>) {
        withAnalytics {
            Firebase.analytics.logEvent(name, params.ifEmpty { null })
        }
    }

    override fun setUserId(userId: String?) {
        withAnalytics {
            Firebase.analytics.setUserId(userId)
        }
        if (userId != null) {
            withCrashlytics {
                Firebase.crashlytics.setUserId(userId)
            }
        }
    }

    override fun setUserProperty(name: String, value: String?) {
        if (value != null) {
            withAnalytics {
                Firebase.analytics.setUserProperty(name, value)
            }
        }
    }

    override fun recordException(throwable: Throwable, message: String?) {
        withCrashlytics {
            if (message != null) Firebase.crashlytics.log(message)
            Firebase.crashlytics.recordException(throwable)
        }
    }

    override fun log(message: String) {
        withCrashlytics {
            Firebase.crashlytics.log(message)
        }
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        withAnalytics { Firebase.analytics.setAnalyticsCollectionEnabled(enabled) }
        withCrashlytics { Firebase.crashlytics.setCrashlyticsCollectionEnabled(enabled) }
    }
}

actual val analyticsModule: Module = module {
    single<Analytics> { FirebaseAnalyticsImpl(context = get()) }
}
