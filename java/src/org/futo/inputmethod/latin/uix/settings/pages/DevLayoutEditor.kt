package org.futo.inputmethod.latin.uix.settings.pages

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.RichInputMethodSubtype
import org.futo.inputmethod.latin.Subtypes
import org.futo.inputmethod.latin.SubtypesSetting
import org.futo.inputmethod.latin.uix.LocalKeyboardScheme
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.getSettingBlocking
import org.futo.inputmethod.latin.uix.setSettingBlocking
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.updates.openURI
import org.futo.inputmethod.v2keyboard.Keyboard
import org.futo.inputmethod.v2keyboard.parseKeyboardYamlString
import java.util.Locale
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Serializable
data class CustomLayout(
    val language: String,
    val layoutYaml: String
) {
    val name: String
        get() =
            try {
                parseKeyboardYamlString(layoutYaml).name
            } catch (_: Exception) {
                (layoutYaml.lines().firstOrNull() ?: "") + "(error compiling)"
            }
}

val CustomLayouts = SettingsKey(
    stringPreferencesKey("dev_customlayouts"),
    "[]"
)

fun getCustomLayouts(context: Context): List<CustomLayout> {
    try {
        val customLayouts = context.getSettingBlocking(CustomLayouts)
        val customLayoutsList: List<CustomLayout> = Json.Default.decodeFromString(customLayouts)

        return customLayoutsList
    } catch(e: Exception) {
        // Something got corrupted, reset it
        Log.e("CustomLayouts", "Failed to parse custom layouts, resetting. ${e.message}")
        context.setSettingBlocking(CustomLayouts.key, CustomLayouts.default)
        return emptyList()
    }
}

fun updateCustomLayoutsAndSyncSubtypes(context: Context, newLayouts: List<CustomLayout>) {
    context.setSettingBlocking(CustomLayouts.key, Json.encodeToString(newLayouts))

    // Remove old custom layouts and add new ones
    val existingSubtypeSet = context.getSettingBlocking(SubtypesSetting)

    val rectifiedList = existingSubtypeSet.filter { v ->
        !v.contains("KeyboardLayoutSet=custom")
    } + newLayouts.mapIndexed { i, it ->
        Subtypes.subtypeToString(Subtypes.makeSubtype(it.language, "custom$i"))
    }

    context.setSettingBlocking(SubtypesSetting.key, rectifiedList.toSet())
}

fun CustomLayout.Companion.getCustomLayout(context: Context, idx: Int): Keyboard =
    parseKeyboardYamlString(getCustomLayouts(context)[idx].layoutYaml)

/** [name] is in the format of `custom\d+`, such as `custom0`, `custom1` */
fun CustomLayout.Companion.getCustomLayout(context: Context, name: String): Keyboard {
    return getCustomLayout(context, name.replace("custom", "").toInt())
}


/** Adds indentation */
private fun handleIndentation(newValue: TextFieldValue, oldValue: TextFieldValue): TextFieldValue {
    // If we have selected text, do not add indentation
    if(oldValue.selection.start != oldValue.selection.end || newValue.selection.start != newValue.selection.end) {
        return newValue
    }

    // If the text hasn't increased in length by 1, do not add indentation
    if(newValue.text.length != (oldValue.text.length + 1)) {
        return newValue
    }

    // If the added character isn't a new line, do not add indentation
    if(newValue.text[newValue.selection.start - 1] != '\n') {
        return newValue
    }

    // Otherwise, we can add indentation

    val lastLine = oldValue.text.substring(0, oldValue.selection.start).substringAfterLast('\n')
    val indent = lastLine.takeWhile { it == ' ' || it == '\t' }

    val newTextWithIndent =
        newValue.text.substring(0, newValue.selection.start) +
                indent +
                newValue.text.substring(newValue.selection.start)

    return newValue.copy(
        text = newTextWithIndent,
        selection = TextRange(start = newValue.selection.start + indent.length, end = newValue.selection.end + indent.length)
    )
}



@OptIn(ExperimentalEncodingApi::class)
@Composable
fun LayoutEditor(layout: CustomLayout, onSave: (CustomLayout) -> Unit, onDelete: () -> Unit) {
    var language by remember { mutableStateOf(TextFieldValue(layout.language)) }
    var layoutYaml by remember { mutableStateOf(TextFieldValue(layout.layoutYaml)) }

    var testText by remember { mutableStateOf(TextFieldValue("")) }

    val textFieldModifier = Modifier
        .fillMaxWidth()
        .padding(top = 4.dp, bottom = 12.dp)
        .background(LocalKeyboardScheme.current.surfaceVariant, RoundedCornerShape(8.dp))
        .border(1.dp, LocalKeyboardScheme.current.outlineVariant, RoundedCornerShape(8.dp))
        .padding(4.dp)

    val style = TextStyle.Default.copy(fontFamily = FontFamily.Monospace, color = LocalKeyboardScheme.current.onSurfaceVariant)

    val options = KeyboardOptions.Default.copy(
        keyboardType = KeyboardType.Password,
        autoCorrect = false
    )

    val cursorBrush = SolidColor(LocalKeyboardScheme.current.onSurfaceVariant)

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Edit Layout", style = MaterialTheme.typography.titleLarge)

        Spacer(modifier = Modifier.height(16.dp))

        Text("Language")
        BasicTextField(
            value = language,
            onValueChange = { language = it },
            modifier = textFieldModifier,
            textStyle = style,
            keyboardOptions = options,
            cursorBrush = cursorBrush
        )

        Text("Layout YAML")
        BasicTextField(
            value = layoutYaml,
            onValueChange = { layoutYaml = handleIndentation(it, layoutYaml) },
            modifier = textFieldModifier
                .height(200.dp),
            textStyle = style,
            keyboardOptions = options,
            cursorBrush = cursorBrush
        )

        Spacer(modifier = Modifier.height(16.dp))


        val layout = CustomLayout(
            language = language.text,
            layoutYaml = layoutYaml.text
        )

        Text("Test the layout")
        BasicTextField(
            value = testText,
            onValueChange = { testText = it },
            modifier = textFieldModifier,
            textStyle = style,
            keyboardOptions = KeyboardOptions.Default.copy(
                platformImeOptions = PlatformImeOptions(
                    privateImeOptions = "org.futo.inputmethod.latin.NoSuggestions=1,org.futo.inputmethod.latin.ForceLocale=${language.text},org.futo.inputmethod.latin.ForceCustomLayoutYamlB64=${
                        Base64.encode(Json.Default.encodeToString(layout).toByteArray()).replace("=", "_")
                    }"
                )
            ),
            cursorBrush = cursorBrush
        )

        Row {
            Button(onClick = {
                // Handle save action
                onSave(
                    CustomLayout(
                        language = language.text,
                        layoutYaml = layoutYaml.text
                    )
                )
            }, modifier = Modifier.weight(1.0f)) {
                Text("Save")
            }

            Spacer(Modifier.width(16.dp))

            Button(onClick = {
                onDelete()
            }, colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )) {
                Text("Delete")
            }
        }
    }
}

private val defaultCustomLayout = CustomLayout(
    language = "en_US",
    layoutYaml = """
name: Example Alphabet Layout
rows:
  - letters: a b c d e f g h i j
  - letters: k l m n o p q r s '
  - letters: t u v w x y z
""".trimIndent()
)


@Composable
fun DevLayoutEdit(navController: NavHostController = rememberNavController(), i: Int) {
    val context = LocalContext.current
    val layout = remember(i) {
        getCustomLayouts(context)[i]
    }

    var backTime by remember { mutableStateOf(0L) }
    BackHandler {
        if(System.currentTimeMillis() < (backTime + 5000L)) {
            navController.navigateUp()
        } else {
            backTime = System.currentTimeMillis()
            Toast.makeText(
                context,
                "Tap back again to cancel your changes and go back.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    ScrollableList {
        ScreenTitle("Custom Layout $i", showBack = false, navController)
        LayoutEditor(layout, onSave = { updated ->
            val newList = getCustomLayouts(context).toMutableList()
            newList.removeAt(i)
            newList.add(i, updated)
            updateCustomLayoutsAndSyncSubtypes(context, newList)
            navController.navigateUp()
        }, onDelete = {
            val newList = getCustomLayouts(context).toMutableList()
            newList.removeAt(i)
            updateCustomLayoutsAndSyncSubtypes(context, newList)
            navController.navigateUp()
        })
    }
}

@Preview(showBackground = true)
@Composable
fun DevLayoutEditor(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val customLayouts: List<CustomLayout> = remember { getCustomLayouts(context) }

    ScrollableList {
        ScreenTitle("Custom Layouts", showBack = true, navController)

        customLayouts.forEachIndexed { i, it ->
            NavigationItem(
                title = it.name,
                style = NavigationItemStyle.MiscNoArrow,
                subtitle = "Custom Layout $i",
                navigate = {
                    navController.navigate("devlayoutedit/$i")
                }
            )
        }

        NavigationItem(
            title = "Create new layout",
            style = NavigationItemStyle.MiscNoArrow,
            navigate = {
                updateCustomLayoutsAndSyncSubtypes(context, customLayouts + listOf(defaultCustomLayout))
                navController.navigate("devlayoutedit/${customLayouts.size}")
            },
            icon = painterResource(R.drawable.plus_circle)
        )

        NavigationItem(
            title = "Layout documentation",
            style = NavigationItemStyle.Misc,
            navigate = {
                context.openURI("https://github.com/futo-org/futo-keyboard-layouts/blob/main/LayoutSpec.md")
            },
        )
    }
}