package com.sirelon.sellsnap.analytics

import org.koin.core.module.Module
import org.koin.dsl.module

private class NoopAnalytics : Analytics {
    override fun logEvent(name: String, params: Map<String, Any>) = Unit
    override fun setUserId(userId: String?) = Unit
    override fun setUserProperty(name: String, value: String?) = Unit
    override fun recordException(throwable: Throwable, message: String?) = Unit
    override fun log(message: String) = Unit
    override fun setCollectionEnabled(enabled: Boolean) = Unit
}

actual val analyticsModule: Module = module {
    single<Analytics> { NoopAnalytics() }
}
