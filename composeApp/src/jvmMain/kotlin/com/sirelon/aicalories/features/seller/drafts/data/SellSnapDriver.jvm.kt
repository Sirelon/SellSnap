package com.sirelon.sellsnap.features.seller.drafts.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.sirelon.sellsnap.db.SellSnapDatabase
import java.io.File
import java.util.Properties

actual fun createSellSnapDriver(): SqlDriver {
    val appDataDir = File(System.getProperty("user.home"), ".sellsnap/datastore")
    appDataDir.mkdirs()
    return JdbcSqliteDriver(
        url = "jdbc:sqlite:${File(appDataDir, "sellsnap.db").absolutePath}",
        properties = Properties(),
        schema = SellSnapDatabase.Schema,
    )
}
