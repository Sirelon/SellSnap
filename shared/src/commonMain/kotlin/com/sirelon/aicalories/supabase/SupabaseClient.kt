package com.sirelon.sellsnap.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.UploadStatus
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.uploadAsFlow
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

private const val STORAGE_BUCKET_NAME = "test"

class SupabaseClient {
    private val sessionMutex = Mutex()

    private val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SupabaseConfig.SUPABASE_URL,
            supabaseKey = SupabaseConfig.SUPABASE_KEY
        ) {
            httpConfig {
                install(HttpTimeout) {
                    requestTimeoutMillis = 60_000
                    connectTimeoutMillis = 15_000
                    socketTimeoutMillis = 60_000
                }
            }
            install(Auth)
            install(Storage)
        }
    }

    fun publicUrl(path: String): String =
        client.storage.from(STORAGE_BUCKET_NAME).publicUrl(path)

    fun uploadFile(path: String, byteArray: ByteArray): Flow<UploadStatus> {
        return flow {
            val userId = ensureAuthenticatedUserId()

            val storagePath = buildStoragePath(userId, path)

            emitAll(
                client
                    .storage
                    .from(STORAGE_BUCKET_NAME)
                    .uploadAsFlow(path = storagePath, data = byteArray)
            )
        }
    }

    private suspend fun ensureAuthenticatedUserId(): String {
        val authPlugin = client.auth
        authPlugin.awaitInitialization()

        return sessionMutex.withLock {
            retrieveUserId(authPlugin) ?: run {
                authenticateWithDefaultCredentials(authPlugin)
                retrieveUserId(authPlugin)
            }
        } ?: error("Unable to determine Supabase user id after authentication")
    }

    private suspend fun authenticateWithDefaultCredentials(authPlugin: Auth) {
        val email = SupabaseConfig.SUPABASE_DEFAULT_EMAIL
        val password = SupabaseConfig.SUPABASE_DEFAULT_PASSWORD

        runCatching {
            authPlugin.signInWith(Email) {
                this.email = email
                this.password = password
            }
        }.recoverCatching { signInError ->
            if (signInError is RestException) {
                // In case the seed user was removed, create it on the fly.
                runCatching {
                    authPlugin.signUpWith(Email) {
                        this.email = email
                        this.password = password
                    }
                }.onFailure { signUpError ->
                    throw signUpError
                }

                authPlugin.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
            } else {
                throw signInError
            }
        }.getOrThrow()
    }

    private suspend fun retrieveUserId(authPlugin: Auth): String? {
        return authPlugin.currentSessionOrNull()?.user?.id ?: runCatching {
            authPlugin.retrieveUserForCurrentSession().id
        }.getOrNull()
    }

    private fun buildStoragePath(userId: String, originalPath: String): String {
        val sanitizedName = originalPath
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .ifBlank { originalPath }
            .trim()

        val safeName = sanitizedName
            .replace(Regex("""[^\w.\-]"""), "_")
            .takeIf { it.isNotBlank() }
            ?: "upload_${Uuid.random()}"

        return "$userId/${Uuid.random()}_$safeName"
    }
}
