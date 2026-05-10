package com.sirelon.sellsnap.features.seller.profile.data

import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthRepository
import com.sirelon.sellsnap.features.seller.auth.domain.OlxAuthorizationRequest
import com.sirelon.sellsnap.features.seller.auth.domain.OlxSessionState
import com.sirelon.sellsnap.features.seller.auth.domain.OlxUser
import com.sirelon.sellsnap.features.seller.location.OlxLocation
import com.sirelon.sellsnap.features.seller.location.data.LocationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SellerAccountRepository(
    private val authRepository: OlxAuthRepository,
    private val olxApiClient: OlxApiClient,
    private val locationRepository: LocationRepository,
) {
    private val _user = MutableStateFlow<OlxUser?>(null)
    val user: StateFlow<OlxUser?> = _user.asStateFlow()

    suspend fun currentSession(): OlxSessionState = authRepository.currentSession()

    suspend fun createAuthorizationRequest(): OlxAuthorizationRequest =
        authRepository.createAuthorizationRequest()

    suspend fun completeAuthorization(callbackUrl: String): Result<OlxUser> = runCatching {
        authRepository.completeAuthorization(callbackUrl).getOrThrow()
        refreshProfile().getOrThrow()
            ?: throw IllegalStateException("OLX account connected, but profile data is unavailable.")
    }

    suspend fun refreshProfile(): Result<OlxUser?> = runCatching {
        val session = authRepository.currentSession()
        if (!session.isAuthorized) {
            _user.value = null
            return@runCatching null
        }

        try {
            olxApiClient.getAuthenticatedUser()
                .also { _user.value = it }
        } catch (error: Throwable) {
            _user.value = null
            throw error
        }
    }

    suspend fun logout() {
        authRepository.logout()
        _user.value = null
    }

    suspend fun savedLocation(): OlxLocation? = locationRepository.getSavedLocation()

    suspend fun refreshLocationFromDevice(): OlxLocation? =
        locationRepository.fetchUserLocation()
}
