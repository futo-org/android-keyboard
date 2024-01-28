package org.futo.inputmethod.latin.uix.settings.pages

import android.app.Activity
import android.content.Intent
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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.Navigator
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.runBlocking
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.settings.IMPORT_GGUF_MODEL_REQUEST
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.Tip
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.latin.xlm.ModelInfo
import org.futo.inputmethod.latin.xlm.ModelPaths
import java.net.URLEncoder


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

@Preview(showBackground = true)
@Composable
fun ManageModelScreen(model: ModelInfo = PreviewModels[0], navController: NavHostController = rememberNavController()) {
    val name = if (model.finetune_count > 0) {
        model.name.trim() + " (local finetune)"
    } else {
        model.name.trim()
    }

    ScrollableList {
        ScreenTitle(name, showBack = true, navController)

        if(model.finetune_count > 0) {
            Tip("This is a version of the model fine-tuned on your private typing data. Avoid sharing the exported file with other people!")
        }
        ScreenTitle("Details")
        val data = listOf(
            listOf("Name", model.name),
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
                modifier = Modifier.fillMaxWidth().border(Dp.Hairline, MaterialTheme.colorScheme.outline).padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { cell ->
                    Text(
                        text = cell,
                        modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                        textAlign = TextAlign.Center,
                        style = Typography.bodyMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        ScreenTitle("Actions")
        NavigationItem(
            title = "Export to file",
            style = NavigationItemStyle.Misc,
            navigate = { }
        )
        NavigationItem(
            title = "Finetune on custom data",
            style = NavigationItemStyle.Misc,
            navigate = { }
        )
        NavigationItem(
            title = "Delete",
            style = NavigationItemStyle.Misc,
            navigate = { }
        )
    }
}

data class ModelViewExtra(val model: ModelInfo) : Navigator.Extras

@Preview(showBackground = true)
@Composable
fun ModelManagerScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val models = if(LocalInspectionMode.current) { PreviewModels } else {
        remember {
            ModelPaths.getModels(context).map {
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
            title = "Explore models",
            style = NavigationItemStyle.Misc,
            navigate = { }
        )
        NavigationItem(
            title = "Import from file",
            style = NavigationItemStyle.Misc,
            navigate = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/octet-stream"

                    // Optionally, specify a URI for the file that should appear in the
                    // system file picker when it loads.
                    //putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
                }

                (context as Activity).startActivityForResult(intent, IMPORT_GGUF_MODEL_REQUEST)
            }
        )
    }
}