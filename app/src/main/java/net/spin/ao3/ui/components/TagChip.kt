package net.spin.ao3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Calm tag pill: a neutral body with a small colored dot that identifies the
 * tag category. One hue per category keeps results readable instead of a
 * rainbow. Use [tinted] for the few semantically-colored chips (ratings).
 */
@Composable
fun TagChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    tinted: Boolean = false,
) {
    val shape = RoundedCornerShape(50)
    val bg = if (tinted) {
        color.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
    }
    val fg = if (tinted) color else MaterialTheme.colorScheme.onSurfaceVariant

    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!tinted) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(color, CircleShape),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (onClick != null) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = bg,
            contentColor = fg,
            onClick = onClick,
        ) {
            content()
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = bg,
            contentColor = fg,
        ) {
            content()
        }
    }
}

// ---- Tag colors (one per category; drive the dot) ---------------------------

val FandomColor = Color(0xFF1E6FD9)
val RatingColor = Color(0xFF8A5A00)
val WarningColor = Color(0xFFC62828)
val CategoryColor = Color(0xFF6A4FA3)
val RelationshipColor = Color(0xFFAD1457)
val CharacterColor = Color(0xFF00796B)
val AdditionalColor = Color(0xFF546E7A)

fun ratingColor(key: String?): Color = when (key) {
    "general-audiences" -> Color(0xFF2E7D32)
    "teen-and-up-audiences" -> Color(0xFF8A5A00)
    "mature" -> Color(0xFFE65100)
    "explicit" -> Color(0xFFC62828)
    "not-rated" -> Color(0xFF546E7A)
    else -> RatingColor
}
