package com.sirelon.sellsnap.features.seller.drafts.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.sirelon.sellsnap.db.SellSnapDatabase

actual fun createSellSnapDriver(): SqlDriver =
    NativeSqliteDriver(schema = SellSnapDatabase.Schema, name = "sellsnap.db")
