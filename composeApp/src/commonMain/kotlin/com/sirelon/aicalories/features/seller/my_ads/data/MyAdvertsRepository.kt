package com.sirelon.sellsnap.features.seller.my_ads.data

import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.my_ads.model.MyAdvertItem
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository

class MyAdvertsRepository(
    private val accountRepository: SellerAccountRepository,
    private val unauthenticatedOlxApiClient: OlxApiClient,
) {
    /** One page of one account's adverts. See [withAccountToken] for why the token is explicit. */
    suspend fun loadAdverts(localIndex: Int, offset: Int, limit: Int): List<MyAdvertItem> =
        accountRepository.withAccountToken(localIndex) { accessToken ->
            unauthenticatedOlxApiClient
                .getCurrentUserAdverts(accessToken, offset = offset, limit = limit)
                .map(MyAdvertItemMapper::map)
        }

    /**
     * Re-reads one advert after a lifecycle command (SIR-101). OLX may resolve a status
     * differently from what was requested - a `finish` can land somewhere other than
     * `RemovedByUser`, and an edit can send an advert back to moderation - so the row is refreshed
     * from the server rather than being patched from the action that was attempted.
     */
    suspend fun loadAdvert(localIndex: Int, advertId: Long): MyAdvertItem =
        accountRepository.withAccountToken(localIndex) { accessToken ->
            MyAdvertItemMapper.map(unauthenticatedOlxApiClient.getAdvert(accessToken, advertId).detail)
        }
}
