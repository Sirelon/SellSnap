package com.sirelon.sellsnap.features.seller.my_ads.data

import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.auth.domain.OlxSessionState
import com.sirelon.sellsnap.features.seller.my_ads.model.MyAdvertItem
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository

class MyAdvertsRepository(
    private val accountRepository: SellerAccountRepository,
    private val olxApiClient: OlxApiClient,
) {
    suspend fun currentSession(): OlxSessionState = accountRepository.currentSession()

    suspend fun loadAdverts(offset: Int, limit: Int): List<MyAdvertItem> =
        olxApiClient
            .getCurrentUserAdverts(offset = offset, limit = limit)
            .map(MyAdvertItemMapper::map)
}
