package net.spin.ao3.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
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
 * Barra inferior Expressive: `ShortNavigationBar` con icono encima y etiqueta
 * debajo (lo que evita que «Biblioteca» se corte). La firma se conserva.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T : BottomBarDestination> CapsuleBottomBar(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    ShortNavigationBar(modifier = modifier) {
        items.forEach { destination ->
            ShortNavigationBarItem(
                selected = destination == selected,
                onClick = { onSelect(destination) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = {
                    Text(
                        stringResource(destination.labelRes),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                iconPosition = NavigationItemIconPosition.Top,
            )
        }
    }
}

/**
 * Rail Expressive para tabletas y escritorio. Comparte los mismos tres
 * destinos; [railExpanded] ensancha el rail desde 1200 dp.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T : BottomBarDestination> PrimaryNavigationRail(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    railExpanded: Boolean,
    modifier: Modifier = Modifier,
) {
    val state = rememberWideNavigationRailState()
    WideNavigationRail(
        modifier = modifier.fillMaxHeight(),
        state = state,
    ) {
        Column(
            modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.forEach { destination ->
                WideNavigationRailItem(
                    selected = destination == selected,
                    onClick = { onSelect(destination) },
                    icon = { Icon(destination.icon, contentDescription = null) },
                    label = { Text(stringResource(destination.labelRes)) },
                    railExpanded = railExpanded,
                )
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

@Preview(showBackground = true, widthDp = 840, heightDp = 800)
@Composable
private fun NavigationRailPreview() {
    Ao3Theme {
        Row {
            PrimaryNavigationRail(
                items = PreviewTab.entries,
                selected = PreviewTab.HOME,
                onSelect = {},
                railExpanded = false,
            )
        }
    }
}
