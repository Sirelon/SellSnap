package com.sirelon.sellsnap.features.seller.drafts.data

import com.sirelon.sellsnap.features.seller.drafts.DraftsRepository
import kotlinx.serialization.json.Json

/**
 * The persistent [DraftsRepository] of the current platform: SQLite on Android, iOS and desktop,
 * process-lifetime memory on the web targets (which have no SQLite driver).
 */
expect fun createDraftsRepository(json: Json): DraftsRepository
