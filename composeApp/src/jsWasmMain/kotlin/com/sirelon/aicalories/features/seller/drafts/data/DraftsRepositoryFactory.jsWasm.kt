package com.sirelon.sellsnap.features.seller.drafts.data

import com.sirelon.sellsnap.features.seller.drafts.DraftsRepository
import com.sirelon.sellsnap.features.seller.drafts.InMemoryDraftsRepository
import kotlinx.serialization.json.Json

actual fun createDraftsRepository(json: Json): DraftsRepository = InMemoryDraftsRepository()
