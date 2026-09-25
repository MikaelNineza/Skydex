package com.skydex.server.db

import org.jetbrains.exposed.v1.jdbc.Database
import java.util.UUID

/** A fresh in-memory H2 database in PostgreSQL mode with the schema created. */
fun testDatabase(): Database = initDatabase(
    createDataSource(
        url = "jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        user = "sa",
        password = "",
    ),
)
