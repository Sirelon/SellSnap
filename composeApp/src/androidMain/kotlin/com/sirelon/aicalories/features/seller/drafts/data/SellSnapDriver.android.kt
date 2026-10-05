package com.sirelon.sellsnap.features.seller.drafts.data

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.sirelon.sellsnap.db.SellSnapDatabase

private var androidContext: Context? = null

fun initAndroidDatabase(context: Context) {
    androidContext = context.applicationContext
}

actual fun createSellSnapDriver(): SqlDriver {
    val context = requireNotNull(androidContext) {
        "Call initAndroidDatabase(applicationContext) before creating the drafts repository."
    }
    return AndroidSqliteDriver(
        schema = SellSnapDatabase.Schema,
        context = context,
        name = "sellsnap.db",
    )
}
