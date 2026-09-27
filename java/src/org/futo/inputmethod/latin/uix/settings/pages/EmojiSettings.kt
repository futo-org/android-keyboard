package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.actions.PersistentEmojiState
import org.futo.inputmethod.latin.uix.actions.emoji.HIDDEN_EMOJIS
import org.futo.inputmethod.latin.uix.actions.emoji.parseEmojiSequenceForHiding
import org.futo.inputmethod.latin.uix.settings.Tip
import org.futo.inputmethod.latin.uix.settings.UserSetting
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu
import org.futo.inputmethod.latin.uix.settings.useDataStore

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun HiddenEmojiEditor() {
    val context = LocalContext.current
    val knownEmojis = PersistentEmojiState.emojis.value
    LaunchedEffect(Unit) {
        if (PersistentEmojiState.emojis.value == null) {
            PersistentEmojiState.loadEmojis(context)
        }
    }

    val (hiddenEmojis, setHiddenEmojis) = useDataStore(
        key = HIDDEN_EMOJIS.key,
        default = HIDDEN_EMOJIS.default
    )
    var newEmoji by remember { mutableStateOf("") }
    var invalidInput by remember { mutableStateOf(false) }

    Row(modifier = Modifier.padding(16.dp, 16.dp, 0.dp, 0.dp)) {
        TextField(
            value = newEmoji,
            onValueChange = {
                newEmoji = it
                invalidInput = false
            },
            modifier = Modifier.weight(1.0f),
            label = { Text(stringResource(R.string.emoji_settings_hidden_add)) },
            isError = invalidInput,
            supportingText = if (invalidInput) {
                { Text(stringResource(R.string.emoji_settings_hidden_invalid)) }
            } else {
                null
            }
        )
        IconButton(
            enabled = newEmoji.isNotBlank() && knownEmojis != null,
            onClick = {
                val parsed = knownEmojis?.let {
                    parseEmojiSequenceForHiding(newEmoji, it.map { emoji -> emoji.emoji })
                }

                if (parsed == null) {
                    invalidInput = true
                } else {
                    setHiddenEmojis(hiddenEmojis + parsed)
                    newEmoji = ""
                    invalidInput = false
                }
            },
            modifier = Modifier.align(Alignment.CenterVertically)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.emoji_settings_hidden_add)
            )
        }
    }

    if (hiddenEmojis.isEmpty()) {
        Tip(stringResource(R.string.emoji_settings_hidden_none))
    } else {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            hiddenEmojis.sorted().forEach { emoji ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = emoji,
                            modifier = Modifier.padding(start = 12.dp, end = 2.dp)
                        )
                        IconButton(
                            onClick = { setHiddenEmojis(hiddenEmojis - emoji) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = stringResource(R.string.emoji_settings_hidden_remove),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

val EmojiSettingsMenu = UserSettingsMenu(
    title = R.string.action_emoji_title,
    navPath = "actions/emoji",
    registerNavPath = true,
    settings = listOf(
        UserSetting(
            name = R.string.emoji_settings_hidden_title,
            subtitle = R.string.emoji_settings_hidden_subtitle
        ) {
            HiddenEmojiEditor()
        }
    )
)
