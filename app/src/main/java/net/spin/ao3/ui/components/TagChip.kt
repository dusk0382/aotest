package net.spin.ao3.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.spin.ao3.ui.theme.Ao3Theme

/**
 * How a tag chip is rendered. One hue per metadata category keeps results
 * instantly scannable (the AO3 metadata map) without a rainbow of accents:
 *
 *  - [TagChipVariant.FILLED_SECONDARY]  solid cocoa container  → fandoms/universo
 *  - [TagChipVariant.FILLED_TERTIARY]   solid sage container   → personajes
 *  - [TagChipVariant.OUTLINED]          outlined + color dot   → relaciones, tags libres
 *  - [TagChipVariant.TINTED]            soft accent fill       → ratings, advertencias
 *  - [TagChipVariant.NEUTRAL]           neutral + color dot    → filtros activos, sugerencias
 */
enum class TagChipVariant { NEUTRAL, FILLED_SECONDARY, FILLED_TERTIARY, TINTED, OUTLINED }

/**
 * M3 metadata chip: 32dp tall with 8dp rounded corners (cápsula suavizada).
 * The [color] drives the semantic accent (tinted fill or leading dot); filled
 * variants resolve their container from the theme tokens.
 */
@Composable
fun TagChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    variant: TagChipVariant = TagChipVariant.NEUTRAL,
) {
    val shape = MaterialTheme.shapes.small
    val (bg, fg, dot) = when (variant) {
        TagChipVariant.NEUTRAL -> Triple(
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurface,
            color,
        )
        TagChipVariant.FILLED_SECONDARY -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            null,
        )
        TagChipVariant.FILLED_TERTIARY -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            null,
        )
        TagChipVariant.TINTED -> Triple(
            // SOLID blend (no translucency): an opaque surface/accent mix gives
            // the same soft accent without the washed-out "translucent tint"
            // look (and stays predictable over any background).
            lerp(MaterialTheme.colorScheme.surface, color, 0.18f),
            color,
            null,
        )
        TagChipVariant.OUTLINED -> Triple(
            MaterialTheme.colorScheme.surfaceContainerLow,
            MaterialTheme.colorScheme.onSurfaceVariant,
            color,
        )
    }
    val border = if (variant == TagChipVariant.OUTLINED) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    } else {
        null
    }

    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .height(32.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dot != null) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(dot, CircleShape),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (onClick != null) {
        Surface(
            // M3 requires a 48x48dp minimum touch target even though the chip
            // itself stays 32dp tall (visual size is not the touch size).
            modifier = modifier.minimumInteractiveComponentSize(),
            shape = shape,
            color = bg,
            contentColor = fg,
            border = border,
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
            border = border,
        ) {
            content()
        }
    }
}

// ---- Tag accent colors (one per category; drive the dot or tinted fill) ------

val FandomColor = Color(0xFF8A6D3B)
val RatingColor = Color(0xFF8A5A00)
val WarningColor = Color(0xFFC62828)
val CategoryColor = Color(0xFF6A4FA3)
val RelationshipColor = Color(0xFFC2185B)
val CharacterColor = Color(0xFF2E7D32)
val AdditionalColor = Color(0xFF757575)

/**
 * Rating accent color. Returns the dark-mode pastel variant on dark themes
 * and a darker, WCAG-safe variant on light themes so the TINTED chip text
 * keeps >= 4.5:1 contrast in both modes.
 */
@Composable
fun ratingColor(key: String?): Color {
    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    return when (key) {
        "general-audiences" -> if (light) Color(0xFF00838F) else Color(0xFF2E9BA6)
        "teen-and-up-audiences" -> if (light) Color(0xFF33691E) else Color(0xFF7CB342)
        "mature" -> if (light) Color(0xFFE65100) else Color(0xFFFFB74D)
        "explicit" -> Color(0xFFC62828)
        "not-rated" -> if (light) Color(0xFF616161) else Color(0xFF9E9E9E)
        else -> RatingColor
    }
}

/**
 * SOLID rating scale (docs/UI.md): a fixed container + text pair per AO3 rating,
 * WCAG AA/AAA in BOTH themes. Ratings are cognitive safety anchors — they get
 * an opaque, instantly recognizable color instead of a translucent tint:
 * E burgundy, M earthy amber, T olive, G deep teal, NR ash gray.
 */
@Composable
fun ratingSolidColors(key: String?): Pair<Color, Color> {
    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    return when (key) {
        "general-audiences" ->
            if (light) Color(0xFFB8ECF4) to Color(0xFF00414A) else Color(0xFF00363A) to Color(0xFF97F0FF)
        "teen-and-up-audiences" ->
            if (light) Color(0xFFC4EFC0) to Color(0xFF1E4A1C) else Color(0xFF1F331E) to Color(0xFFB8F0B2)
        "mature" ->
            if (light) Color(0xFFF5E1AC) to Color(0xFF564200) else Color(0xFF412D00) to Color(0xFFFFDF9E)
        "explicit" ->
            if (light) Color(0xFFFFD9D4) to Color(0xFF7D2119) else Color(0xFF4A1215) to Color(0xFFFFB4AB)
        "not-rated" ->
            if (light) Color(0xFFDEDEDE) to Color(0xFF333333) else Color(0xFF2D2D2D) to Color(0xFFE2E2E2)
        // Unknown rating keys share the archive-warning magenta family.
        else ->
            if (light) Color(0xFFF8D7EC) to Color(0xFF6E1B54) else Color(0xFF3B0029) to Color(0xFFFFB0E1)
    }
}

/** Solid container/text pair for archive warnings (shared with Explicit per spec). */
@Composable
fun warningSolidColors(): Pair<Color, Color> {
    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    return if (light) Color(0xFFFFD9D4) to Color(0xFF7D2119) else Color(0xFF4A1215) to Color(0xFFFFB4AB)
}

/**
 * A chip with an explicit SOLID container/text pair — for the metadata that
 * must stay recognizable at a glance (ratings, warnings, completion state).
 */
@Composable
fun TagChipSolid(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.small
    Surface(
        modifier = modifier,
        shape = shape,
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier
                .height(32.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview
@Composable
private fun TagChipPreview() {
    Ao3Theme {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TagChip("Fandom · Harry Potter", FandomColor, variant = TagChipVariant.FILLED_SECONDARY, onClick = {})
            TagChip("Personaje · Hermione", CharacterColor, variant = TagChipVariant.FILLED_TERTIARY, onClick = {})
            TagChip("Relación · Draco/Hermione", RelationshipColor, variant = TagChipVariant.OUTLINED, onClick = {})
            TagChip("Advertencia · Muerte", WarningColor, variant = TagChipVariant.TINTED)
            TagChip("Etiqueta libre", AdditionalColor)
        }
    }
}
