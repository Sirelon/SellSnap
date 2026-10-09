package com.sirelon.sellsnap.fakes

import com.sirelon.sellsnap.model.SocialAccount
import com.sirelon.sellsnap.model.SocialPost
import com.sirelon.sellsnap.store.SocialStore

/** Counts the single-document reads that `/go` makes, to prove a request read nothing. */
class CountingStore(private val delegate: SocialStore) : SocialStore by delegate {
    var postReads = 0
        private set
    var accountReads = 0
        private set

    val reads: Int get() = postReads + accountReads

    override suspend fun post(id: String): SocialPost? {
        postReads++
        return delegate.post(id)
    }

    override suspend fun account(id: String): SocialAccount? {
        accountReads++
        return delegate.account(id)
    }
}
