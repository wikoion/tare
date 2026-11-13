package tare.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import tare.db.TareDb

actual fun createSqlDriver(name: String): SqlDriver = NativeSqliteDriver(TareDb.Schema, name)
