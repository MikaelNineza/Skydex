package com.skydex.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import javax.sql.DataSource

/** A connection pool for [url]. Fails fast if the database can't be reached. */
fun createDataSource(url: String, user: String, password: String): HikariDataSource =
    HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = url
            username = user
            this.password = password
            maximumPoolSize = 5
        },
    )

/** Connects Exposed to [dataSource], creates any missing tables and adds columns newer than the tables. */
fun initDatabase(dataSource: DataSource): Database {
    val db = Database.connect(dataSource)
    transaction(db) {
        SchemaUtils.create(Devices, Snapshots, SentAlerts)
        // There is no migration framework; columns added after release are added here, idempotently.
        exec("ALTER TABLE devices ADD COLUMN IF NOT EXISTS jacob_crops TEXT NOT NULL DEFAULT ''")
    }
    return db
}

/** Runs [block] in a transaction on the IO dispatcher, since JDBC blocks. */
suspend fun <T> Database.query(block: JdbcTransaction.() -> T): T =
    withContext(Dispatchers.IO) { transaction(this@query) { block() } }
