package com.skydex.shared.model

import kotlinx.serialization.Serializable

/** Response body of the server's `GET /health` endpoint. */
@Serializable
data class ServerHealth(
    val status: String,
    val version: String,
)
