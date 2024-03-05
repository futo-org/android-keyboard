package org.futo.inputmethod.latin.uix.settings.pages.modelmanager

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.uix.USE_TRANSFORMER_FINETUNING
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.Tip
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.latin.uix.urlEncode
import org.futo.inputmethod.latin.xlm.MODEL_OPTION_KEY
import org.futo.inputmethod.latin.xlm.ModelInfo
import org.futo.inputmethod.latin.xlm.ModelInfoLoader
import org.futo.inputmethod.latin.xlm.ModelPaths
import org.futo.inputmethod.updates.openURI
import java.io.File


@Composable
fun DamagedModelScreen(model: ModelInfoLoader, navController: NavHostController = rememberNavController()) {
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

    val finetuningEnabled = useDataStore(key = USE_TRANSFORMER_FINETUNING.key, default = USE_TRANSFORMER_FINETUNING.default)

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
            listOf("Number of finetuning runs", model.finetune_count.toString()),
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
                        ModelPaths.updateModelOption(context, lang, file)
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

        if(finetuningEnabled.value) {
            NavigationItem(
                title = "Finetune model",
                style = NavigationItemStyle.Misc,
                navigate = {
                    navController.navigate("finetune/${model.path.urlEncode()}")
                }
            )
        }

        NavigationItem(
            title = "Delete",
            style = NavigationItemStyle.Misc,
            navigate = {
                navController.navigate("modelDelete/${model.path.urlEncode()}")
            }
        )
    }
}