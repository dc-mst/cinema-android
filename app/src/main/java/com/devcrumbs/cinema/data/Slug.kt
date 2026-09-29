package com.devcrumbs.cinema.data

import java.text.Normalizer

// Ports of the website's frontend/src/utils/slug.ts. The film page is
// /api/film/<slug>, and the screening feed carries no slug: like the website,
// the app builds it from the title. Keep the rules identical.

/** Lowercase, strip diacritics, strip punctuation, collapse whitespace. */
fun normaliseTitle(title: String): String =
    Normalizer.normalize(title.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("[\\u0300-\\u036f]"), "")
        .replace(Regex("[^a-z0-9 ]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

/** Hyphen-joined slug; "" when the title has nothing alphanumeric. */
fun slugify(title: String): String = normaliseTitle(title).replace(" ", "-")

/** Slug of a TMDB-only film's page: title slug + "-<year>" when the year is known. */
fun buildTmdbSlug(title: String, year: Int?): String {
    val base = slugify(title)
    return if (base.isEmpty() || year == null || year == 0) base else "$base-$year"
}
