package net.spin.ao3.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
 * M3 metadata chip. Normal: 32dp con labelMedium (hojas de tags, filtros).
 * [dense]: 26dp con labelSmall — para dentro de tarjetas, donde el chip es
 * un enlace secundario y no debe comerse la tarjeta.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TagChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    variant: TagChipVariant = TagChipVariant.NEUTRAL,
    dense: Boolean = false,
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
                .height(if (dense) 26.dp else 32.dp)
                .padding(horizontal = if (dense) 10.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dot != null) {
                Box(
                    Modifier
                        .size(if (dense) 5.dp else 6.dp)
                        .background(dot, CircleShape),
                )
                Spacer(Modifier.width(if (dense) 5.dp else 6.dp))
            }
            Text(
                text = text,
                style = if (dense) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (onClick != null && !dense) {
        FilterChip(
            selected = false,
            onClick = onClick,
            label = content,
            shapes = FilterChipDefaults.shapes(),
            modifier = modifier.heightIn(min = 48.dp),
            colors = FilterChipDefaults.filterChipColors(
                containerColor = bg,
                labelColor = fg,
            ),
            border = border,
        )
    } else {
        // Denso con click: Surface clicable — el chip es un enlace secundario
        // dentro de una tarjeta que ya es el objetivo táctil completo.
        val m = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
        Surface(
            modifier = m,
            shape = shape,
            color = bg,
            contentColor = fg,
            border = border,
        ) {
            content()
        }
    }
}

// ---- Paleta de chips (skill android-design: roles, no hex sueltos) ---------
// Cada categoría de metadato AO3 se pinta con un ROL del esquema, no con hex
// hechos a mano: se adapta solo a claro/oscuro/contraste/Material You y los
// pares contenedor/contenido garantizan el contraste. El reparto por rango:
//
//  - Relaciones   → OUTLINED  + punto `primary` (el tag principal de AO3)
//  - Fandoms      → FILLED_SECONDARY (secondaryContainer = familia hielo)
//  - Personajes   → FILLED_TERTIARY  (tertiaryContainer)
//  - Libres       → OUTLINED  + punto `onSurfaceVariant` (sabor, se retira)
//  - Categorías   → OUTLINED  + punto `onSurfaceVariant` (estructural, callado)
//  - Valoración   → TagChipSolid con su escala semántica (ancla de seguridad)
//  - Advertencias → TagChipSolid rojo (peligro, semántica fija)
//  - Estado       → TINTED success (semántica de la app)
//  - Filtros      → NEUTRAL   + punto `primary`
//
// Los hex sueltos anteriores (7 tonos discordantes con la marca) se eliminan:
// la consistencia viene de usar SIEMPRE el mismo rol para la misma categoría.

/**
 * Rating accent color. Returns the dark-mode pastel variant on dark themes
 * and a darker, WCAG-safe variant on light themes so the TINTED chip text
 * keeps >= 4.5:1 contrast in both modes.
 */
@Composable
fun ratingColor(key: String?): Color {
    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    return Color(ratingTextHex(key, light))
}

/**
 * SOLID rating scale (docs/UI.md): a fixed container + text pair per AO3 rating,
 * WCAG AA/AAA in BOTH themes. Ratings are cognitive safety anchors — they get
 * an opaque, instantly recognizable color instead of a translucent tint:
 * G sky, T green, M amber, E red, NR gray. All pairs verified at 9.6:1 or
 * better (light) and 5.5:1 or better (dark).
 */
@Composable
fun ratingSolidColors(key: String?): Pair<Color, Color> {
    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    return ratingSolidHex(key, light).let { (container, text) -> Color(container) to Color(text) }
}

/** Solid container/text pair for archive warnings. */
@Composable
fun warningSolidColors(): Pair<Color, Color> {
    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    return warningSolidHex(light).let { (container, text) -> Color(container) to Color(text) }
}

/**
 * A chip with an explicit SOLID container/text pair — for the metadata that
 * must stay recognizable at a glance (ratings, warnings, completion state).
 * [compact] reduce la píldora a 24 dp con labelSmall: para dentro de tarjetas
 * donde la valoración es un ancla discreta, no un titular.
 */
@Composable
fun TagChipSolid(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val shape = if (compact) RoundedCornerShape(50) else MaterialTheme.shapes.small
    Surface(
        modifier = modifier,
        shape = shape,
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier
                .height(if (compact) 24.dp else 32.dp)
                .padding(horizontal = if (compact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
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
            TagChip("Fandom · Harry Potter", MaterialTheme.colorScheme.secondary, variant = TagChipVariant.FILLED_SECONDARY, onClick = {})
            TagChip("Personaje · Hermione", MaterialTheme.colorScheme.tertiary, variant = TagChipVariant.FILLED_TERTIARY, onClick = {})
            TagChip("Relación · Draco/Hermione", MaterialTheme.colorScheme.primary, variant = TagChipVariant.OUTLINED, onClick = {})
            TagChip("Advertencia · Muerte", MaterialTheme.colorScheme.error, variant = TagChipVariant.TINTED)
            TagChip("Etiqueta libre", MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
