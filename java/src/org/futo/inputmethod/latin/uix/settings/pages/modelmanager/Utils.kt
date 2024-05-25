package org.futo.inputmethod.latin.uix.settings.pages.modelmanager

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.uix.settings.EXPORT_GGUF_MODEL_REQUEST
import org.futo.inputmethod.latin.uix.settings.IMPORT_GGUF_MODEL_REQUEST
import org.futo.inputmethod.latin.uix.settings.SettingsActivity
import org.futo.inputmethod.latin.xlm.ModelInfo
import org.futo.inputmethod.latin.xlm.ModelInfoLoader
import java.io.File
import java.text.CharacterIterator
import java.text.StringCharacterIterator


fun humanReadableByteCountSI(bytes: Long): String {
    var bytes = bytes
    if (-1000 < bytes && bytes < 1000) {
        return "$bytes B"
    }
    val ci: CharacterIterator = StringCharacterIterator("kMGTPE")
    while (bytes <= -999950 || bytes >= 999950) {
        bytes /= 1000
        ci.next()
    }
    return String.format("%.1f %cB", bytes / 1000.0, ci.current())
}

private fun findSettingsActivity(context: Context): SettingsActivity {
    if(context is SettingsActivity) {
        return context
    }else if(context is ContextThemeWrapper){
        if(context.baseContext == context) throw IllegalStateException("Infinite loop detected in ContextThemeWrapper")
        return findSettingsActivity(context.baseContext)
    }else{
        throw IllegalArgumentException("Context provided is not one of SettingsActivity or ContextThemeWrapper")
    }
}

fun triggerModelExport(context: Context, file: File) {
    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "application/octet-stream"

        putExtra(Intent.EXTRA_TITLE, file.name)
    }

    val activity: SettingsActivity = findSettingsActivity(context)

    activity.updateFileBeingSaved(file)
    activity.startActivityForResult(intent, EXPORT_GGUF_MODEL_REQUEST)
}

@Composable
fun ModelScreenNav(file: File, navController: NavHostController = rememberNavController()) {
    val loader = remember { ModelInfoLoader(name = file.nameWithoutExtension, path = file) }
    val model = remember { loader.loadDetails() }
    if(model != null) {
        ManageModelScreen(model = model, navController)
    } else {
        DamagedModelScreen(model = loader, navController)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPicker(
    label: String,
    options: List<ModelInfo>,
    modelSelection: ModelInfo?,
    onSetModel: (ModelInfo) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = {
                expanded = !expanded
            },
            modifier = Modifier.align(Alignment.Center)
        ) {
            TextField(
                readOnly = true,
                value = modelSelection?.name ?: "Auto",
                onValueChange = { },
                label = { Text(label) },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = expanded
                    )
                },
                colors = ExposedDropdownMenuDefaults.textFieldColors(
                    focusedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    focusedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    focusedIndicatorColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    focusedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = {
                    expanded = false
                }
            ) {
                options.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = {
                            Text(selectionOption.name)
                        },
                        onClick = {
                            onSetModel(selectionOption)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}


fun openModelImporter(context: Context) {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "application/octet-stream"
    }

    (context as Activity).startActivityForResult(intent, IMPORT_GGUF_MODEL_REQUEST)
}