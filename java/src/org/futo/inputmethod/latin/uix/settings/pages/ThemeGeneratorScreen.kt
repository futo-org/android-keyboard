package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import androidx.navigation.NavHostController
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.CustomAccentColor
import org.futo.inputmethod.latin.uix.CustomBaseColor
import org.futo.inputmethod.latin.uix.CustomIconColor
import org.futo.inputmethod.latin.uix.CustomIconBgColor
import org.futo.inputmethod.latin.uix.CustomKeyBgColor
import org.futo.inputmethod.latin.uix.CustomModifierColor
import org.futo.inputmethod.latin.uix.CustomBorderColor
import org.futo.inputmethod.latin.uix.CustomBackgroundImage
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.theme.selector.ThemePreview
import org.futo.inputmethod.latin.uix.theme.presets.CustomTheme

@Composable
fun ThemeGeneratorScreen(navController: NavHostController) {
    val (accent, setAccent) = useDataStore(CustomAccentColor)
    val (base, setBase) = useDataStore(CustomBaseColor)
    val (icon, setIcon) = useDataStore(CustomIconColor)
    val (iconBg, setIconBg) = useDataStore(CustomIconBgColor)
    val (keyBg, setKeyBg) = useDataStore(CustomKeyBgColor)
    val (modBg, setModBg) = useDataStore(CustomModifierColor)
    val (border, setBorder) = useDataStore(CustomBorderColor)
    val (bgImage, setBgImage) = useDataStore(CustomBackgroundImage)
    Column(Modifier.fillMaxSize()) {
        ScreenTitle(stringResource(R.string.theme_generator_title), showBack = true, navController)
        ColorPicker(stringResource(R.string.theme_generator_accent), accent, setAccent)
        ColorPicker(stringResource(R.string.theme_generator_base), base, setBase)
        ColorPicker(stringResource(R.string.theme_generator_icon), icon, setIcon)
        ColorPicker("Icon Background", iconBg, setIconBg)
        ColorPicker("Key Background", keyBg, setKeyBg)
        ColorPicker("Modifier Key", modBg, setModBg)
        ColorPicker("Key Border", border, setBorder)
        TextFieldWithLabel("Background Image", bgImage, setBgImage)
        Button(onClick = { navController.navigateUp() }, modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.theme_generator_save))
        }
        ThemePreview(CustomTheme, modifier = Modifier.padding(16.dp)) {}
    }
}

@Composable
private fun ColorPicker(label: String, colorStr: String, setColor: (String) -> Job) {
    fun toHex(c: Color): String = String.format("#%06X", 0xFFFFFF and c.toArgb())
    var color by remember(colorStr) { mutableStateOf(runCatching { Color(android.graphics.Color.parseColor(colorStr)) }.getOrDefault(Color.White)) }
    val update = { setColor(toHex(color)) }
    Column(Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
        Text(label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp, 20.dp).background(color))
        }
        Slider(value = color.red, onValueChange = { color = color.copy(red = it); update() }, colors = SliderDefaults.colors(thumbColor = Color.Red, activeTrackColor = Color.Red))
        Slider(value = color.green, onValueChange = { color = color.copy(green = it); update() }, colors = SliderDefaults.colors(thumbColor = Color.Green, activeTrackColor = Color.Green))
        Slider(value = color.blue, onValueChange = { color = color.copy(blue = it); update() }, colors = SliderDefaults.colors(thumbColor = Color.Blue, activeTrackColor = Color.Blue))
    }
}

@Composable
private fun TextFieldWithLabel(label: String, value: String, setValue: (String) -> Job) {
    var text by remember(value) { mutableStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
        Text(label)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; setValue(it) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
