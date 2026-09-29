package com.devcrumbs.cinema.data

// The festivals the backend (app/festivals.py) writes into festival_premieres,
// with the website's badge initials (frontend/src/utils/festivals.tsx).
private val FESTIVALS = mapOf(
    "cannes" to ("C" to "Cannes"),
    "venice" to ("V" to "Venezia"),
    "berlinale" to ("B" to "Berlinale"),
    "locarno" to ("L" to "Locarno"),
    "sundance" to ("S" to "Sundance"),
    "tiff" to ("TO" to "Toronto"),
    "nyff" to ("NY" to "New York"),
    "sansebastian" to ("SS" to "San Sebastián"),
    "london" to ("LN" to "London"),
    "telluride" to ("TL" to "Telluride"),
    "rotterdam" to ("R" to "Rotterdam"),
    "sxsw" to ("SX" to "SXSW"),
)

fun festivalInitial(slug: String): String = FESTIVALS[slug]?.first ?: "?"

fun festivalName(slug: String): String = FESTIVALS[slug]?.second ?: slug

/** One entry per festival (earliest date), chronological — as the website does. */
fun dedupeFestivalPremieres(list: List<FestivalPremiere>?): List<FestivalPremiere> =
    list.orEmpty()
        .groupBy { it.festival }
        .map { (_, entries) -> entries.minBy { it.date.orEmpty() } }
        .sortedBy { it.date.orEmpty() }
