package net.spin.ao3.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.spin.ao3.R
import net.spin.ao3.ui.theme.Ao3Theme

/** A single destination of the bottom navigation bar. */
interface BottomBarDestination {
    @get:StringRes val labelRes: Int
    val icon: ImageVector
}

/**
 * M3-style bottom navigation bar with a standard 64x32dp capsule indicator
 * (primaryContainer + onPrimaryContainer) for the selected destination.
 * Unselected items show as plain icon + label in onSurfaceVariant.
 */
@Composable
fun <T : BottomBarDestination> CapsuleBottomBar(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(72.dp)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEach { destination ->
                val isSelected = destination == selected
                // Animated pill: colors and icon size glide between states.
                val pillColor by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    label = "pillColor",
                )
                val pillContent by animateColorAsState(
                    if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "pillContent",
                )
                val iconSize by animateDpAsState(if (isSelected) 24.dp else 22.dp, label = "iconSize")
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp)
                        .clickable { onSelect(destination) },
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = pillColor,
                        contentColor = pillContent,
                        modifier = Modifier
                            .height(32.dp)
                            .widthIn(min = 64.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                destination.icon,
                                contentDescription = null,
                                modifier = Modifier.size(iconSize),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(destination.labelRes),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class PreviewTab(
    @StringRes override val labelRes: Int,
    override val icon: ImageVector,
) : BottomBarDestination {
    HOME(R.string.tab_home, Icons.Filled.Home),
    LIBRARY(R.string.tab_library, Icons.AutoMirrored.Filled.List),
    SETTINGS(R.string.tab_settings, Icons.Filled.Settings),
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun BottomBarPreview() {
    Ao3Theme {
        CapsuleBottomBar(items = PreviewTab.entries, selected = PreviewTab.HOME, onSelect = {})
    }
}
