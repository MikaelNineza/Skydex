package com.skydex.shared.model

import kotlinx.serialization.Serializable

/**
 * `PUT /v1/devices/{installationId}` (204 No Content). Upserts a device's push registration.
 * `DELETE /v1/devices/{installationId}` (204) removes it.
 *
 * The installation id is a random UUID the app generates once and stores.
 */
@Serializable
data class DeviceRegistration(
    /** Empty until Firebase issues a token; the server skips alerts for the device until then. */
    val fcmToken: String,
    /** Events this device wants a push for. Names the server doesn't know (from a newer app) are dropped. */
    @Serializable(with = LenientEventTypeSet::class)
    val subscribedEvents: Set<EventType> = emptySet(),
    /** How long before an event starts to send the push. */
    val leadMinutes: Int = 5,
    /** Crops that make a Jacob's contest worth a push. Empty = any crop. */
    @Serializable(with = LenientCropSet::class)
    val jacobCrops: Set<Crop> = emptySet(),
)

/** Body of every non-2xx response from the server. */
@Serializable
data class ApiError(
    val message: String,
)
