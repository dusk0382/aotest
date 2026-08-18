package net.spin.ao3.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.delay
import net.spin.ao3.data.model.TagSuggestion

// ---- Comma-separated tag string helpers (multi-tag fields) -------------------
//
// The search model stores tags as a comma-separated string, so the multi-tag
// picker treats the LAST segment as "being typed" and everything before it as
// committed chips. A segment is committed only when a comma follows it, which
// keeps the model unambiguous while the user edits.

/** Segments of [input] that are committed (i.e. followed by a comma). */
fun committedTagSegments(input: String): List<String> =
    input.split(',')
        .dropLast(1)
        .map { it.trim() }
        .filter { it.isNotEmpty() }

/** The last (still being typed) segment of [input]. */
fun activeTagSegment(input: String): String = input.substringAfterLast(',').trim()

/**
 * Appends [tag] to the committed part of [input] (drops the active segment).
 * The result ends with ", " so the new tag stays committed while the user
 * types the next one; the trailing comma is trimmed when the search is sent.
 */
fun appendCommittedTag(input: String, tag: String): String =
    (committedTagSegments(input) + tag).joinToString(", ") + ", "

/** Replaces the active segment of [input] with [newActive] (keeps chips). */
fun replaceActiveSegment(input: String, newActive: String): String =
    (committedTagSegments(input) + newActive).joinToString(", ")

/** Removes the committed tag at [index], keeping the active segment. */
fun removeCommittedTag(input: String, index: Int): String {
    val committed = committedTagSegments(input).toMutableList()
    if (index !in committed.indices) return input
    committed.removeAt(index)
    val active = activeTagSegment(input)
    return (committed + active).joinToString(", ")
}

/**
 * Text field with AO3 tag autocomplete (the same `/autocomplete/{type}` JSON
 * endpoint AO3's own web UI uses), modelled after CO3's AutocompleteInput:
 *
 *  - suggestions only for terms of >= 2 characters, debounced 300 ms;
 *  - a loading row while the fetch is in flight;
 *  - a dropdown of suggestions while focused (dismissed on blur/selection);
 *  - case-insensitive dedupe against the committed chips / current value;
 *  - free-form fallback: submitting the keyboard action commits whatever is
 *    typed even if AO3 suggests nothing (searching must never block on the
 *    network).
 *
 * Two modes:
 *  - [singleSelect] = true: the field holds the whole value (e.g. "Fandom a
 *    explorar"); selecting a suggestion fills the field.
 *  - [singleSelect] = false: [chips] render the committed tags of a
 *    comma-separated value; the field only shows the segment being typed;
 *    [onSelectSuggestion] receives the picked tag (append it with
 *    [appendCommittedTag]) and the field is cleared automatically.
 *
 * Stale responses can never land: the fetch runs in a LaunchedEffect keyed on
 * the term, so a new keystroke cancels the in-flight request before its result
 * could be applied (CO3's debounce has no such guard).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutocompleteTagField(
    label: String,
    placeholder: String,
    fetchSuggestions: suspend (String) -> List<TagSuggestion>,
    value: String,
    onValueChange: (String) -> Unit,
    onSelectSuggestion: (TagSuggestion) -> Unit,
    chips: List<String> = emptyList(),
    onRemoveChip: ((Int) -> Unit)? = null,
    onSubmit: (() -> Unit)? = null,
    singleSelect: Boolean = false,
    /** Semantic accent for the chips (fandom/personaje/relación/adicional);
     *  defaults to the theme's secondary container like a neutral chip. */
    accent: Color = MaterialTheme.colorScheme.secondaryContainer,
    onAccent: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    modifier: Modifier = Modifier,
) {
    var suggestions by remember { mutableStateOf<List<TagSuggestion>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }

    // Debounced + auto-cancelling fetch: keyed on the term, so each keystroke
    // cancels the previous in-flight request (no stale result can be applied).
    LaunchedEffect(value) {
        suggestions = emptyList()
        loading = false
        val term = value.trim()
        if (term.length < 2) return@LaunchedEffect
        delay(300)
        loading = true
        try {
            suggestions = fetchSuggestions(term)
        } finally {
            loading = false
        }
    }

    fun select(s: TagSuggestion) {
        val already = if (singleSelect) {
            value.trim().equals(s.name, ignoreCase = true)
        } else {
            chips.any { it.equals(s.name, ignoreCase = true) } ||
                value.trim().equals(s.name, ignoreCase = true)
        }
        if (already) {
            // Already selected: just drop the term being typed.
            if (!singleSelect) onValueChange("")
        } else {
            onSelectSuggestion(s)
            if (!singleSelect) onValueChange("")
        }
        suggestions = emptyList()
    }

    Column(modifier = modifier) {
        if (chips.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                chips.forEachIndexed { index, chip ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = accent,
                        contentColor = onAccent,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                        ) {
                            Text(
                                chip,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (onRemoveChip != null) {
                                IconButton(
                                    onClick = { onRemoveChip(index) },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Quitar $chip",
                                        tint = onAccent,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Limpiar", modifier = Modifier.size(18.dp))
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit?.invoke() }),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
        )

        if (loading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp),
            ) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Buscando tags…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (focused && suggestions.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            ) {
                Column(
                    Modifier
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                ) {
                    suggestions.forEach { s ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { select(s) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(
                                s.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Normal,
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
