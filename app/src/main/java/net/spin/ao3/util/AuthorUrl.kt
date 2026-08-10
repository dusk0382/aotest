package net.spin.ao3.util

/**
 * Extracts the AO3 username from an author link like
 * `/users/Vichan`, `/users/Vichan/pseuds/k-vichan` or a full URL.
 * Returns null for guest/anonymous comments (no href).
 */
fun usernameFromAuthorUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val path = if (url.startsWith("http")) {
        runCatching { java.net.URI(url).path }.getOrNull() ?: return null
    } else {
        url
    }
    val after = path.substringAfter("/users/", missingDelimiterValue = "")
    if (after.isEmpty()) return null
    return after.substringBefore("/").substringBefore("?").ifEmpty { null }
}
