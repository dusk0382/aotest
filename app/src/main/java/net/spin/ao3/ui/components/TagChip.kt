package net.spin.ao3.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TagChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(50)
    if (onClick != null) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = color.copy(alpha = 0.15f),
            contentColor = color,
            onClick = onClick,
        ) {
            ChipText(text)
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = color.copy(alpha = 0.15f),
            contentColor = color,
        ) {
            ChipText(text)
        }
    }
}

@Composable
private fun ChipText(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
    )
}

// ---- Tag colors ------------------------------------------------------------

val FandomColor = Color(0xFF1E6FD9)
val RatingColor = Color(0xFF8A5A00)
val WarningColor = Color(0xFFC62828)
val CategoryColor = Color(0xFF7B1FA2)
val RelationshipColor = Color(0xFFAD1457)
val CharacterColor = Color(0xFF00695C)
val AdditionalColor = Color(0xFF546E7A)

fun ratingColor(key: String?): Color = when (key) {
    "general-audiences" -> Color(0xFF2E7D32)
    "teen-and-up-audiences" -> Color(0xFFF9A825)
    "mature" -> Color(0xFFE65100)
    "explicit" -> Color(0xFFC62828)
    "not-rated" -> Color(0xFF546E7A)
    else -> RatingColor
}
