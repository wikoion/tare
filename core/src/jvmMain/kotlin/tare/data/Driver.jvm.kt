package tare.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import tare.db.TareDb

/** [name] is a file path, or [JdbcSqliteDriver.IN_MEMORY] for tests. */
actual fun createSqlDriver(name: String): SqlDriver {
    val url = if (name == JdbcSqliteDriver.IN_MEMORY) name else "jdbc:sqlite:$name"
    return JdbcSqliteDriver(url).also { TareDb.Schema.create(it) }
}
