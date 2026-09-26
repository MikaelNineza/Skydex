package com.skydex.app.ui.events

import androidx.annotation.DrawableRes
import com.skydex.app.R
import com.skydex.shared.model.EventType

// Event icons are SkyCrypt's item renders (sky.shiiyu.moe/api/item/<ID>), downloaded once and bundled in
// res/drawable-nodpi, like the skill icons: the app never fetches them at runtime.

/** Bundled icon for an event. */
@DrawableRes
fun eventIcon(type: EventType): Int = when (type) {
    EventType.DARK_AUCTION -> R.drawable.event_dark_auction
    EventType.JACOBS_CONTEST -> R.drawable.event_jacobs_contest
    EventType.CULT_OF_THE_FALLEN_STAR -> R.drawable.event_cult_of_the_fallen_star
    EventType.TRAVELING_ZOO -> R.drawable.event_traveling_zoo
    EventType.SPOOKY_FESTIVAL -> R.drawable.event_spooky_festival
    EventType.SEASON_OF_JERRY -> R.drawable.event_season_of_jerry
    EventType.NEW_YEAR_CELEBRATION -> R.drawable.event_new_year_celebration
    EventType.BANK_INTEREST -> R.drawable.event_bank_interest
    EventType.FISHING_FESTIVAL -> R.drawable.event_fishing_festival
    EventType.MINING_FIESTA -> R.drawable.event_mining_fiesta
    EventType.JERRYS_WORKSHOP -> R.drawable.event_jerrys_workshop
    EventType.HOPPITYS_HUNT -> R.drawable.event_hoppitys_hunt
    EventType.ELECTION_OPEN -> R.drawable.event_election_open
    EventType.MAYOR_TERM_CHANGE -> R.drawable.event_mayor_term_change
    EventType.YEAR_OF_THE_SEAL -> R.drawable.event_year_of_the_seal
    EventType.YEAR_OF_THE_WITCH -> R.drawable.event_year_of_the_witch
    EventType.YEAR_OF_THE_PIG -> R.drawable.event_year_of_the_pig
}
