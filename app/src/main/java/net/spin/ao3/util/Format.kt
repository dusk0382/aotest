package net.spin.ao3.util

import java.util.Locale

/** 1234567 -> "1.2M", 12345 -> "12K", 1234 -> "1.2K" */
fun formatCount(n: Long): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
    n >= 100_000 -> String.format(Locale.US, "%.0fK", n / 1000.0)
    n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1000.0)
    else -> n.toString()
}

/** Escapes text for safe embedding in HTML. */
fun escapeHtml(text: String): String = buildString(text.length) {
    for (c in text) {
        when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(c)
        }
    }
}
