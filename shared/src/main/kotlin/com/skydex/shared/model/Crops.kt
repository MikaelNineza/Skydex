package com.skydex.shared.model

import kotlinx.serialization.Serializable

/** Crops that can appear in a Jacob's Farming Contest. [displayName] is the spelling api.elitebot.dev uses. */
@Serializable
enum class Crop(val displayName: String) {
    CACTUS("Cactus"),
    CARROT("Carrot"),
    COCOA_BEANS("Cocoa Beans"),
    MELON("Melon"),
    MOONFLOWER("Moonflower"),
    MUSHROOM("Mushroom"),
    NETHER_WART("Nether Wart"),
    POTATO("Potato"),
    PUMPKIN("Pumpkin"),
    SUGAR_CANE("Sugar Cane"),
    SUNFLOWER("Sunflower"),
    WHEAT("Wheat"),
    WILD_ROSE("Wild Rose"),
    ;

    companion object {
        /** The crop spelled [name], or null for one we don't know. */
        fun fromDisplayName(name: String): Crop? = entries.find { it.displayName == name }
    }
}

/** `GET /v1/contests`: one Jacob's contest and its three crops, soonest first. */
@Serializable
data class JacobContest(
    /** Unix millis. */
    val startsAt: Long,
    /** Crops this app doesn't know (added by a newer server) are dropped. */
    @Serializable(with = LenientCropList::class)
    val crops: List<Crop>,
)
