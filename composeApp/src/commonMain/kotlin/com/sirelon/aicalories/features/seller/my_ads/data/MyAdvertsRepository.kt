package com.sirelon.sellsnap.features.seller.my_ads.data

import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.my_ads.model.MyAdvertItem

class MyAdvertsRepository(
    private val olxApiClient: OlxApiClient,
) {
    suspend fun loadAdverts(offset: Int, limit: Int): List<MyAdvertItem> =
        olxApiClient
            .getCurrentUserAdverts(offset = offset, limit = limit)
            .map(MyAdvertItemMapper::map)
}
