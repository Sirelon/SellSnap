package com.sirelon.sellsnap.features.seller.drafts.data

import app.cash.sqldelight.db.SqlDriver
import com.sirelon.sellsnap.db.SellSnapDatabase
import com.sirelon.sellsnap.features.seller.drafts.DraftsRepository
import kotlinx.serialization.json.Json

/** The SQLite driver for `sellsnap.db`, which creates or migrates the [SellSnapDatabase] schema. */
expect fun createSellSnapDriver(): SqlDriver

actual fun createDraftsRepository(json: Json): DraftsRepository =
    SqlDelightDraftsRepository(
        database = SellSnapDatabase(createSellSnapDriver()),
        json = json,
    )
