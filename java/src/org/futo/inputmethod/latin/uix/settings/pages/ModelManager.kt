package org.futo.inputmethod.latin.uix.settings.pages

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.Intent.EXTRA_TITLE
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.settings.EXPORT_GGUF_MODEL_REQUEST
import org.futo.inputmethod.latin.uix.settings.IMPORT_GGUF_MODEL_REQUEST
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingsActivity
import org.futo.inputmethod.latin.uix.settings.Tip
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.latin.uix.urlEncode
import org.futo.inputmethod.latin.xlm.MODEL_OPTION_KEY
import org.futo.inputmethod.latin.xlm.ModelInfo
import org.futo.inputmethod.latin.xlm.ModelInfoLoader
import org.futo.inputmethod.latin.xlm.ModelPaths
import org.futo.inputmethod.latin.xlm.ModelPaths.updateModelOption
import org.futo.inputmethod.latin.xlm.TrainingState
import org.futo.inputmethod.latin.xlm.TrainingStateWithModel
import org.futo.inputmethod.latin.xlm.TrainingWorkerStatus
import org.futo.inputmethod.latin.xlm.scheduleTrainingWorkerImmediately
import org.futo.inputmethod.updates.openURI
import java.io.File
import java.net.URLEncoder
import java.text.CharacterIterator
import java.text.StringCharacterIterator
import kotlin.math.roundToInt


val PreviewModelLoader = ModelInfoLoader(path = File("/tmp/badmodel.gguf"), name = "badmodel")

val PreviewModels = listOf(
    ModelInfo(
        name = "ml4_model",
        description = "A simple model",
        author = "FUTO",
        license = "GPL",
        features = listOf("inverted_space", "xbu_char_autocorrect_v1", "char_embed_mixing_v1"),
        languages = listOf("en-US"),
        tokenizer_type = "Embedded SentencePiece",
        finetune_count = 16,
        path = "?"
    ),


    ModelInfo(
        name = "ml4_model",
        description = "A simple model",
        author = "FUTO",
        license = "GPL",
        features = listOf("inverted_space", "xbu_char_autocorrect_v1", "char_embed_mixing_v1"),
        languages = listOf("en-US"),
        tokenizer_type = "Embedded SentencePiece",
        finetune_count = 0,
        path = "?"
    ),


    ModelInfo(
        name = "gruby",
        description = "Polish Model",
        author = "FUTO",
        license = "GPL",
        features = listOf("inverted_space", "xbu_char_autocorrect_v1", "char_embed_mixing_v1"),
        languages = listOf("pl"),
        tokenizer_type = "Embedded SentencePiece",
        finetune_count = 23,
        path = "?"
    ),

    ModelInfo(
        name = "gruby",
        description = "Polish Model",
        author = "FUTO",
        license = "GPL",
        features = listOf("inverted_space", "xbu_char_autocorrect_v1", "char_embed_mixing_v1"),
        languages = listOf("pl"),
        tokenizer_type = "Embedded SentencePiece",
        finetune_count = 0,
        path = "?"
    ),
)

fun triggerModelExport(context: Context, file: File) {
    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "application/octet-stream"

        putExtra(EXTRA_TITLE, file.name)
    }

    val activity = context as SettingsActivity
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

@Preview
@Composable
fun ModelDeleteConfirmScreen(path: File = File("/example"), navController: NavHostController = rememberNavController()) {
    AlertDialog(
        icon = {
            Icon(Icons.Filled.Warning, contentDescription = "Error")
        },
        title = {
            Text(text = "Delete model \"${path.nameWithoutExtension}\"")
        },
        text = {
            Text(text = "Are you sure you want to delete this model? You will not be able to recover it. If this model was finetuned, everything it learned will be lost.")
        },
        onDismissRequest = {
            navController.navigateUp()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    path.delete()
                    navController.navigateUp()
                    navController.navigateUp()
                }
            ) {
                Text(stringResource(R.string.delete_dict))
            } 
        },
        dismissButton = {
            TextButton(
                onClick = {
                    navController.navigateUp()
                }
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Preview
@Composable
fun PrivateModelExportConfirmation(path: File = File("/example"), navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    AlertDialog(
        icon = {
            Icon(Icons.Filled.Warning, contentDescription = "Error")
        },
        title = {
            Text(text = "PRIVACY WARNING - \"${path.nameWithoutExtension}\"")
        },
        text = {
            Text(text = "This model has been tainted with your personal data through finetuning. If you share the exported file, others may be able to reconstruct things you've typed.\n\nExporting is intended for transferring between devices or backup. We do not recommend sharing the exported file.")
        },
        onDismissRequest = {
            navController.navigateUp()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    triggerModelExport(context, path)
                }
            ) {
                Text("I understand")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    navController.navigateUp()
                }
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun DamagedModelScreen(model: ModelInfoLoader = PreviewModelLoader, navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current


    ScrollableList {
        ScreenTitle(model.name, showBack = true, navController)

        Tip("This model is damaged, its metadata could not be loaded. It may be corrupt or it may not be a valid model file.")

        NavigationItem(
            title = "Visit FAQ",
            style = NavigationItemStyle.Misc,
            navigate = {
                context.openURI("https://gitlab.futo.org/alex/futo-keyboard-lm-docs/-/blob/main/README.md")
            }
        )
        NavigationItem(
            title = "Export to file",
            style = NavigationItemStyle.Misc,
            navigate = { triggerModelExport(context, model.path) }
        )
        NavigationItem(
            title = "Delete",
            style = NavigationItemStyle.Misc,
            navigate = {
                navController.navigate("modelDelete/${model.path.absolutePath.urlEncode()}")
            }
        )
    }
}

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


@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun FinetuneModelScreen(file: File? = null, navController: NavHostController = rememberNavController()) {
    val model = remember { file?.let { ModelInfoLoader(name = it.nameWithoutExtension, path = it).loadDetails() } }

    val context = LocalContext.current
    val models = if(!LocalInspectionMode.current) {
        remember { runBlocking { ModelPaths.getModelOptions(context) }.values.mapNotNull { it.loadDetails() } }
    } else {
        PreviewModels
    }

    val trainingState = TrainingWorkerStatus.state.collectAsState(initial = TrainingStateWithModel(TrainingState.None, null))
    val currentModel = remember { mutableStateOf(model) }

    val progress = TrainingWorkerStatus.progress.collectAsState(initial = 0.0f)
    val loss = TrainingWorkerStatus.loss.collectAsState(initial = Float.MAX_VALUE)

    val customData = remember { mutableStateOf("") }

    ScrollableList {
        ScreenTitle("Finetuning", showBack = true, navController)

        if(trainingState.value.state == TrainingState.Training && TrainingWorkerStatus.isTraining.value) {
            Text("Currently busy finetuning ${trainingState.value.model}")
            Text("Progress ${(progress.value * 100.0f).roundToInt()}%")
            Text("Loss ${loss.value}")
        } else {
            if(trainingState.value.state != TrainingState.None && trainingState.value.model == currentModel.value?.toLoader()?.path?.nameWithoutExtension) {
                when(trainingState.value.state) {
                    TrainingState.None -> {} // unreachable
                    TrainingState.Training -> {} // unreachable
                    TrainingState.ErrorInadequateData -> {
                        Text("Last training run failed due to lack of data")
                    }
                    TrainingState.Finished -> {
                        Text("Last training run succeeded with final loss ${loss.value}")
                    }
                    TrainingState.FatalError -> {
                        Text("Fatal error")
                    }
                }
            }

            ModelPicker("Model", models, currentModel.value) { currentModel.value = it }

            TextField(value = customData.value, onValueChange = { customData.value = it }, placeholder = {
                Text("Custom training data. Leave blank for none", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f))
            })

            Button(onClick = {
                println("PATH ${currentModel.value?.toLoader()?.path?.absolutePath}, ${currentModel.value?.toLoader()?.path?.exists()}")
                scheduleTrainingWorkerImmediately(
                    context,
                    model = currentModel.value?.toLoader(),
                    trainingData = if(customData.value.isEmpty()) { null } else { customData.value }
                )
            }) {
                Text("Start Training")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ManageModelScreen(model: ModelInfo = PreviewModels[0], navController: NavHostController = rememberNavController()) {
    val name = remember {
        if (model.finetune_count > 0) {
            model.name.trim() + " (local finetune)"
        } else {
            model.name.trim()
        }
    }

    val context = LocalContext.current

    val file = remember { File(model.path) }

    val fileSize = remember {
        humanReadableByteCountSI(file.length())
    }

    val coroutineScope = LocalLifecycleOwner.current

    val modelOptions = useDataStore(key = MODEL_OPTION_KEY.key, default = MODEL_OPTION_KEY.default)

    ScrollableList {
        ScreenTitle(name, showBack = true, navController)

        if(model.finetune_count > 0) {
            Tip("This is a version of the model fine-tuned on your private typing data. Avoid sharing the exported file with other people!")
        }

        if(model.features.isEmpty() || model.tokenizer_type == "None" || model.languages.isEmpty()) {
            Tip("This model does not appear to be supported, you may not be able to use it.")
        }
        ScreenTitle("Details")
        val data = listOf(
            listOf("Name", model.name),
            listOf("Filename", file.name),
            listOf("Size", fileSize),
            listOf("Description", model.description),
            listOf("Author", model.author),
            listOf("License", model.license),
            listOf("Languages", model.languages.joinToString(" ")),
            listOf("Features", model.features.joinToString(" ")),
            listOf("Tokenizer", model.tokenizer_type),
            listOf("Finetune Count", model.finetune_count.toString()),
        )

        data.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(Dp.Hairline, MaterialTheme.colorScheme.outline)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { cell ->
                    Text(
                        text = cell,
                        modifier = Modifier
                            .weight(1f)
                            .align(Alignment.CenterVertically),
                        textAlign = TextAlign.Center,
                        style = Typography.bodyMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        ScreenTitle("Defaults")

        model.languages.forEach { lang ->
            val isDefaultOption = modelOptions.value.firstOrNull {
                it.startsWith("$lang:")
            }?.split(":", limit = 2)?.get(1) == file.nameWithoutExtension


            val text = if(isDefaultOption) {
                "Model is set to default for $lang"
            } else {
                "Set default model for $lang"
            }

            val style = if(isDefaultOption) {
                NavigationItemStyle.MiscNoArrow
            } else {
                NavigationItemStyle.Misc
            }

            NavigationItem(
                title = text,
                style = style,
                navigate = {
                    coroutineScope.lifecycleScope.launch {
                        updateModelOption(context, lang, file)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        ScreenTitle("Actions")

        NavigationItem(
            title = "Export to file",
            style = NavigationItemStyle.Misc,
            navigate = {
                if(model.finetune_count > 0) {
                    navController.navigate("modelExport/${model.path.urlEncode()}")
                } else {
                    triggerModelExport(context, file)
                }
            }
        )
        NavigationItem(
            title = "Finetune on custom data",
            style = NavigationItemStyle.Misc,
            navigate = {
                navController.navigate("finetune/${model.path.urlEncode()}")
            }
        )
        NavigationItem(
            title = "Delete",
            style = NavigationItemStyle.Misc,
            navigate = {
                navController.navigate("modelDelete/${model.path.urlEncode()}")
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ModelManagerScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val models = if(LocalInspectionMode.current) { PreviewModels } else {
        remember {
            ModelPaths.getModels(context).mapNotNull {
                it.loadDetails()
            }
        }
    }

    val modelChoices = remember { runBlocking { ModelPaths.getModelOptions(context) } }

    val modelsByLanguage: MutableMap<String, MutableList<ModelInfo>> = mutableMapOf()
    models.forEach { model ->
        modelsByLanguage.getOrPut(model.languages.joinToString(" ")) { mutableListOf() }.add(model)
    }

    ScrollableList {
        ScreenTitle("Models", showBack = true, navController)

        modelsByLanguage.forEach { item ->
            Spacer(modifier = Modifier.height(32.dp))
            ScreenTitle(item.key)

            item.value.forEach { model ->
                val name = if (model.finetune_count > 0) {
                    model.name.trim() + " (local finetune)"
                } else {
                    model.name.trim()
                }

                val style = if (model.path == modelChoices[item.key]?.path?.absolutePath) {
                    NavigationItemStyle.HomePrimary
                } else {
                    NavigationItemStyle.MiscNoArrow
                }

                NavigationItem(
                    title = name,
                    style = style,
                    navigate = {
                        navController.navigate("model/${URLEncoder.encode(model.path, "utf-8")}")
                    },
                    icon = painterResource(id = R.drawable.cpu)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        ScreenTitle("Actions")
        NavigationItem(
            title = "FAQ",
            style = NavigationItemStyle.Misc,
            navigate = {
                context.openURI("https://gitlab.futo.org/alex/futo-keyboard-lm-docs/-/blob/main/README.md")
            }
        )
        NavigationItem(
            title = "Import from file",
            style = NavigationItemStyle.Misc,
            navigate = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/octet-stream"
                }

                (context as Activity).startActivityForResult(intent, IMPORT_GGUF_MODEL_REQUEST)
            }
        )
    }
}