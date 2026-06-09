package com.sirelon.sellsnap.features.seller.currency.data

import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.currency.domain.OlxCurrency
import kotlinx.coroutines.CancellationException

class CurrencyRepository(
    private val olxApiClient: OlxApiClient,
) {
    suspend fun getDefaultCurrency(): OlxCurrency {
        return try {
            olxApiClient.loadCurrencies()
                .firstOrNull { it.isDefault }
                ?: OlxCurrency.Default
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            OlxCurrency.Default
        }
    }
}
