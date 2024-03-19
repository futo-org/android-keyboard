package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.uix.SUGGESTION_BLACKLIST
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingItem
import org.futo.inputmethod.latin.uix.settings.SettingToggleSharedPrefs
import org.futo.inputmethod.latin.uix.settings.Tip
import org.futo.inputmethod.latin.uix.settings.useDataStore

@Composable
fun BlacklistedWord(word: String, remove: () -> Unit) { 
    SettingItem(word) {
        IconButton(onClick = remove) {
            Icon(Icons.Default.Clear, contentDescription = "Remove")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun BlacklistScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val (blacklistedWords, setBlacklistedWords) = useDataStore(key = SUGGESTION_BLACKLIST.key, default = SUGGESTION_BLACKLIST.default)

    var newWord by remember { mutableStateOf("") }
    ScrollableList {
        ScreenTitle("Word Blacklist", showBack = true, navController)

        SettingToggleSharedPrefs(
            title = stringResource(R.string.prefs_block_potentially_offensive_title),
            subtitle = stringResource(R.string.prefs_block_potentially_offensive_summary),
            key = Settings.PREF_BLOCK_POTENTIALLY_OFFENSIVE,
            default = booleanResource(R.bool.config_block_potentially_offensive)
        )

        Row(modifier = Modifier.padding(16.dp, 16.dp, 0.dp, 0.dp)) {
            TextField(value = newWord, onValueChange = {newWord = it}, modifier = Modifier.weight(1.0f), label = {Text("Add word to blacklist")})
            IconButton(onClick = {
                val newSet = blacklistedWords.toMutableSet()
                newSet.add(newWord)
                setBlacklistedWords(newSet)
                newWord = ""
            }, modifier = Modifier.align(Alignment.CenterVertically)) {
                Icon(Icons.Default.Add, contentDescription = "Add to blacklist")
            }
        }

        if(blacklistedWords.isEmpty()) {
            Tip("There are no blacklisted words.")
        }
        blacklistedWords.forEach {
            BlacklistedWord(word = it) {
                val newSet = blacklistedWords.toMutableSet()
                newSet.remove(it)
                setBlacklistedWords(newSet)
            }
        }
    }
}


@Preview
@Composable
fun PreviewBlacklist() {
    Column {
        BlacklistedWord(word = "Hello") {
            
        }
        
        BlacklistedWord(word = "Goodbye") {
            
        }
    }
}