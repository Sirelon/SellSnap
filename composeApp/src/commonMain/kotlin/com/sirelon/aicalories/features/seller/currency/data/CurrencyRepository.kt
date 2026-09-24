package com.sirelon.sellsnap.features.seller.currency.data

import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.auth.data.OlxCountryStore
import com.sirelon.sellsnap.features.seller.currency.domain.OlxCurrency
import kotlinx.coroutines.CancellationException

class CurrencyRepository(
    private val olxApiClient: OlxApiClient,
    private val countryStore: OlxCountryStore,
) {
    suspend fun getDefaultCurrency(): OlxCurrency {
        return try {
            olxApiClient.loadCurrencies()
                .firstOrNull { it.isDefault }
                ?: OlxCurrency.fallbackFor(countryStore.current)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            OlxCurrency.fallbackFor(countryStore.current)
        }
    }
}
