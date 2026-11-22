package net.spin.ao3.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Hoja adaptativa: bottom sheet expandida en pantallas compactas o de poca
 * altura; diálogo acotado en tabletas y escritorio. El contenido (y su scroll)
 * lo aporta quien llama — aquí sólo se decide el contenedor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints {
        val compact = maxWidth < 840.dp || maxHeight < 480.dp
        if (compact) {
            val sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
            )
            ModalBottomSheet(
                onDismissRequest = onDismissRequest,
                sheetState = sheetState,
                modifier = modifier,
                dragHandle = {
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .height(24.dp),
                    )
                },
            ) {
                content()
            }
        } else {
            Dialog(
                onDismissRequest = onDismissRequest,
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Surface(
                    modifier = modifier
                        .fillMaxWidth(0.9f)
                        .widthIn(max = 720.dp)
                        .fillMaxHeight(0.92f),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    Column(content = content)
                }
            }
        }
    }
}
