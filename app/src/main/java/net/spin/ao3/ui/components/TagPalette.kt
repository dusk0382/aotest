package net.spin.ao3.ui.components

/**
 * Paleta pura (JVM, sin Android/Compose) para los chips sólidos de ratings y
 * advertencias. Los longs son ARGB `0xFF........`; en modo oscuro las parejas
 * container/texto se invierten. Los ratios WCAG de cada par están calculados en
 * `docs/EXPRESSIVE_FLASH_PLAN.md` (todos >= 4.5:1, mínimo observado 4.59:1).
 */

private const val GENERAL_CONTAINER = 0xFFC2E7FF
private const val GENERAL_TEXT = 0xFF001E30
private const val TEEN_CONTAINER = 0xFFB9F6CA
private const val TEEN_TEXT = 0xFF00210A
private const val MATURE_CONTAINER = 0xFFFFDEA3
private const val MATURE_TEXT = 0xFF2A1800
private const val EXPLICIT_CONTAINER = 0xFFFFDAD6
private const val EXPLICIT_TEXT = 0xFF410002
private const val NOT_RATED_CONTAINER = 0xFFE1E3E6
private const val NOT_RATED_TEXT = 0xFF1A1C1E
private const val WARNING_CONTAINER = 0xFFFFD7DC
private const val WARNING_TEXT = 0xFF4A0711

/** Color de acento del rating para texto/bola/punto sobre superficie mezclada. */
internal fun ratingTextHex(key: String?, light: Boolean): Long = when (key) {
    "general-audiences" -> if (light) 0xFF005B85 else 0xFF9BD3F0
    "teen-and-up-audiences" -> if (light) 0xFF146B45 else 0xFF8CD9AC
    "mature" -> if (light) 0xFF855200 else 0xFFF0C77C
    "explicit" -> if (light) 0xFF9E2838 else 0xFFFF9FAA
    "not-rated" -> if (light) 0xFF52616C else 0xFFBCC7D0
    else -> if (light) 0xFF52616C else 0xFFBCC7D0
}

/** Par sólido (container, texto) para un rating; en oscuro se invierte. */
internal fun ratingSolidHex(key: String?, light: Boolean): Pair<Long, Long> = when (key) {
    "general-audiences" -> GENERAL_CONTAINER to GENERAL_TEXT
    "teen-and-up-audiences" -> TEEN_CONTAINER to TEEN_TEXT
    "mature" -> MATURE_CONTAINER to MATURE_TEXT
    "explicit" -> EXPLICIT_CONTAINER to EXPLICIT_TEXT
    "not-rated" -> NOT_RATED_CONTAINER to NOT_RATED_TEXT
    else -> WARNING_CONTAINER to WARNING_TEXT
}.let { if (light) it else it.second to it.first }

/** Par sólido (container, texto) para advertencias de archivo. */
internal fun warningSolidHex(light: Boolean): Pair<Long, Long> =
    (WARNING_CONTAINER to WARNING_TEXT).let { if (light) it else it.second to it.first }
