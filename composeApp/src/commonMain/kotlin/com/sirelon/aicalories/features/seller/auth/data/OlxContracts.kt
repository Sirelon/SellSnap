package com.sirelon.sellsnap.features.seller.auth.data

import com.sirelon.sellsnap.features.seller.auth.domain.OlxAuthCallback
import com.sirelon.sellsnap.features.seller.auth.domain.OlxLaunchResult
import com.sirelon.sellsnap.features.seller.auth.domain.OlxPendingAuthSession
import com.sirelon.sellsnap.features.seller.auth.domain.OlxTokens
import com.sirelon.sellsnap.datastore.KeyValueStore
import com.sirelon.sellsnap.datastore.createKeyValueStore
import com.sirelon.sellsnap.platform.PlatformTargets
import kotlinx.serialization.json.Json

interface OlxCredentialsProvider {
    suspend fun getClientId(): String

    suspend fun getClientSecret(): String
}

class OlxTokenStore internal constructor(
    private val storage: KeyValueStore,
    private val json: Json,
) {
    constructor(json: Json) : this(createKeyValueStore("olx_tokens"), json)

    suspend fun read(): OlxTokens? =
        storage.getString(KEY)?.let { json.decodeFromString<OlxTokens>(it) }

    suspend fun write(tokens: OlxTokens) =
        storage.putString(KEY, json.encodeToString<OlxTokens>(tokens))

    suspend fun clear() = storage.remove(KEY)

    private companion object {
        const val KEY = "tokens"
    }
}

class OlxAuthSessionStore internal constructor(
    private val storage: KeyValueStore,
    private val json: Json,
) {
    constructor(json: Json) : this(createKeyValueStore("olx_auth_session"), json)

    suspend fun read(): OlxPendingAuthSession? =
        storage.getString(KEY)?.let { json.decodeFromString<OlxPendingAuthSession>(it) }

    suspend fun write(session: OlxPendingAuthSession) =
        storage.putString(KEY, json.encodeToString<OlxPendingAuthSession>(session))

    suspend fun clear() = storage.remove(KEY)

    private companion object {
        const val KEY = "session"
    }
}

class GuestModeStore internal constructor(private val storage: KeyValueStore) {
    constructor() : this(createKeyValueStore("seller_session"))

    suspend fun isGuest(): Boolean = storage.getString(KEY) == VALUE_TRUE

    suspend fun setGuest(enabled: Boolean) {
        if (enabled) storage.putString(KEY, VALUE_TRUE) else storage.remove(KEY)
    }

    private companion object {
        const val KEY = "guest_mode"
        const val VALUE_TRUE = "true"
    }
}

interface OlxRedirectHandler {
    fun buildRedirectUri(platform: PlatformTargets = PlatformTargets): String

    fun parseCallback(url: String): OlxAuthCallback
}

interface OlxExternalAuthLauncher {
    suspend fun launch(url: String): OlxLaunchResult
}

class BuildConfigOlxCredentialsProvider : OlxCredentialsProvider {
    override suspend fun getClientId(): String = OlxConfig.clientId

    override suspend fun getClientSecret(): String = OlxConfig.clientSecret
}
