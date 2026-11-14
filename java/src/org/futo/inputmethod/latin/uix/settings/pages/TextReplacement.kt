package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.TextReplacement
import org.futo.inputmethod.latin.TextReplacements
import org.futo.inputmethod.latin.uix.TEXT_REPLACEMENTS
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingItem
import org.futo.inputmethod.latin.uix.settings.Tip
import org.futo.inputmethod.latin.uix.settings.useDataStore

@Composable
private fun TextReplacementItem(entry: TextReplacement, remove: () -> Unit) {
    SettingItem(title = entry.shortcut, subtitle = entry.replacement) {
        IconButton(onClick = remove) {
            Icon(
                Icons.Default.Clear,
                contentDescription = stringResource(R.string.text_replacement_remove)
            )
        }
    }
}

@Composable
private fun TextReplacementEditor(
    isDuplicate: (String) -> Boolean,
    add: (TextReplacement) -> Unit
) {
    var shortcut by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }

    val duplicate = shortcut.isNotEmpty() && isDuplicate(shortcut)

    Column(modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 0.dp)) {
        TextField(
            value = shortcut,
            onValueChange = { shortcut = it.trim() },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = duplicate,
            label = { Text(stringResource(R.string.text_replacement_shortcut)) },
            placeholder = { Text("omw") },
            supportingText = if (duplicate) {
                { Text(stringResource(R.string.text_replacement_duplicate_shortcut)) }
            } else {
                null
            }
        )

        Spacer(modifier = Modifier.padding(4.dp))

        // Taller than the shortcut field, both to make room for multiline replacements and to
        // make it obvious which field is which.
        TextField(
            value = replacement,
            onValueChange = { replacement = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
            label = { Text(stringResource(R.string.text_replacement_replacement)) },
            placeholder = { Text("On my way!") }
        )

        Row(modifier = Modifier.padding(0.dp, 8.dp)) {
            Spacer(modifier = Modifier.weight(1.0f))

            TextButton(onClick = {
                shortcut = ""
                replacement = ""
            }) {
                Text(stringResource(R.string.text_replacement_cancel))
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    add(TextReplacement(shortcut = shortcut, replacement = replacement))
                    shortcut = ""
                    replacement = ""
                },
                enabled = shortcut.isNotEmpty() && replacement.isNotEmpty() && !duplicate
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.text_replacement_add))
            }
        }
    }
}

@Preview
@Composable
fun TextReplacementScreen(navController: NavHostController = rememberNavController()) {
    val (encoded, setEncoded) = useDataStore(
        key = TEXT_REPLACEMENTS.key,
        default = TEXT_REPLACEMENTS.default
    )

    val replacements = remember(encoded) { TextReplacements.decode(encoded) }

    ScrollableList {
        ScreenTitle(stringResource(R.string.text_replacement_title), showBack = true, navController)

        Tip(stringResource(R.string.text_replacement_explanation))

        TextReplacementEditor(
            // Matching is case-insensitive, so "OMW" and "omw" would be the same shortcut
            isDuplicate = { shortcut -> replacements.any { it.shortcut.equals(shortcut, true) } },
            add = { setEncoded(TextReplacements.encode(replacements + it)) }
        )

        if (replacements.isEmpty()) {
            Tip(stringResource(R.string.text_replacement_none))
        }

        replacements.forEach { entry ->
            TextReplacementItem(entry) {
                setEncoded(TextReplacements.encode(replacements.filter { it != entry }))
            }
        }
    }
}

@Preview
@Composable
private fun PreviewTextReplacementItems() {
    Column {
        TextReplacementItem(TextReplacement("omw", "On my way!")) { }
        TextReplacementItem(TextReplacement("@@", "example@example.com")) { }
    }
}
