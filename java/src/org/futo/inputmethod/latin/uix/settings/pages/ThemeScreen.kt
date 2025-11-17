package org.futo.inputmethod.latin.uix.settings.pages

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextInputService
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TextInputSession
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.google.android.material.color.utilities.Hct
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.KeyBordersSetting
import org.futo.inputmethod.latin.uix.THEME_KEY
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.setSetting
import org.futo.inputmethod.latin.uix.settings.DataStoreItem
import org.futo.inputmethod.latin.uix.settings.RotatingChevronIcon
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingSliderForDataStoreItem
import org.futo.inputmethod.latin.uix.settings.SettingToggleDataStore
import org.futo.inputmethod.latin.uix.settings.SettingToggleRaw
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.theme.CustomThemeBuilderConfiguration
import org.futo.inputmethod.latin.uix.theme.SerializableCustomTheme
import org.futo.inputmethod.latin.uix.theme.fromKeyboardScheme
import org.futo.inputmethod.latin.uix.theme.selector.ThemePicker
import org.futo.inputmethod.latin.uix.theme.toSColor
import java.io.File
import kotlin.math.pow

val Context.customThemePhoto get() = File(filesDir, "customtheme.png")


@JvmInline
@Serializable
value class Hct2(val v: Triple<Double, Double, Double>)

@SuppressLint("RestrictedApi")
@Composable
fun HctSliderPicker(
    initialHct: Hct2,
    onColorChange: (Hct2) -> Unit
) {
    val hct = remember(initialHct) { Hct.from(initialHct.v.first, initialHct.v.second, initialHct.v.third) }

    var hue   by remember { mutableFloatStateOf(hct.hue.toFloat()) }
    var chroma by remember { mutableFloatStateOf(hct.chroma.toFloat()) }
    var tone  by remember { mutableFloatStateOf(hct.tone.toFloat()) }

    LaunchedEffect(hue, chroma, tone) {
        onColorChange(Hct2(Triple(hue.toDouble(), chroma.toDouble(), tone.toDouble())))
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        LabeledSlider(
            label = "Hue",
            value = hue,
            valueRange = 0f..360f,
            gradient = Brush.linearGradient(
                (0..360 step 30).map { h ->
                    Color(Hct.from(h.toDouble(), 100.0, 50.0).toInt())
                }
            ),
            onValueChange = { hue = it }
        )

        LabeledSlider(
            label = "Chroma",
            value = chroma,
            valueRange = 0f..114.0f,
            gradient = Brush.linearGradient(
                (0..114 step 10).map { c ->
                    Color(Hct.from(hue.toDouble(), c.toDouble(), 50.0).toInt())
                }
            ),
            onValueChange = { chroma = it }
        )

        LabeledSlider(
            label = "Tone",
            value = tone,
            valueRange = 0f..100f,
            gradient = Brush.linearGradient(
                (0..100 step 10).map { t ->
                    Color(Hct.from(hue.toDouble(), 0.0, t.toDouble()).toInt())
                }
            ),
            onValueChange = { tone = it }
        )
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    gradient: Brush,
    onValueChange: (Float) -> Unit
) {
    Column {
        //Text(label, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
        ) {

            val widthPx = this.constraints.maxWidth.toFloat()
            val fraction = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawRect(brush = gradient)
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            val newPosition = change.position.x
                            val newValue = newPosition.coerceIn(0f, widthPx) / widthPx * (valueRange.endInclusive - valueRange.start)
                            onValueChange(newValue)
                        }
                    }
                    .clip(CircleShape)
            )

            Box(
                modifier = Modifier
                    .offset { IntOffset((fraction * widthPx - 12.dp.toPx()).toInt(), 0) }
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .align(Alignment.CenterStart)
            )
        }
    }
}


@Preview(showBackground=true)
@Composable
fun CustomThemeScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val color = remember { mutableStateOf(Hct2(Triple(0.0, 100.0, 50.0))) }
    //val contrast = remember { mutableFloatStateOf(0.0f) }
    val opacity = remember { mutableFloatStateOf(0.2f) }
    val roundness = remember { mutableFloatStateOf(1.0f) }
    val darkMode = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val pickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri ?: return@rememberLauncherForActivityResult
            try {
                val resolver = context.contentResolver
                val displayWidth = context.resources.displayMetrics.widthPixels

                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }

                val (w, h) = options.run { outWidth to outHeight }
                val sample = maxOf(w / displayWidth, h / displayWidth).coerceAtLeast(1)

                val scaled = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }.let { opts ->
                    resolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, opts)
                    }
                } ?: return@rememberLauncherForActivityResult

                context.customThemePhoto.outputStream().use { out ->
                    scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                scaled.recycle()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    )

    val scheme = remember(color.value, darkMode.value, opacity.floatValue, roundness.floatValue) {
        val contrast = color.value.v.third.toFloat() / 100.0f

        val cfg = CustomThemeBuilderConfiguration(
            hue = color.value.v.first,
            chroma = color.value.v.second,
            tone = 50.0,
            darkMode = darkMode.value,
            amoledDark = false,
            contrast = contrast,
        )
        cfg.buildScheme().let {
            if(!darkMode.value) {
                val surfaceModifier = cfg.getTone(75.0).copy(alpha = (1.0f - contrast) * 0.8f + 0.1f)
                it.copy(
                    extended = it.extended.copy(
                        keyboardContainerVariant = it.surfaceTint.copy(alpha = (1.0f - contrast) * 0.2f)
                            .compositeOver(it.surfaceContainer),
                        keyboardContainer = it.surfaceContainerLowest.copy(alpha = contrast)
                            .compositeOver(it.surfaceContainer),
                        keyboardSurface = surfaceModifier
                            .compositeOver(it.surfaceContainerHigh),
                        keyboardSurfaceDim = it.surfaceTint.copy(alpha = (1.0f - contrast) * 0.07f)
                            .compositeOver(surfaceModifier).compositeOver(it.surfaceContainerHighest)
                    )
                )
            } else {
                val contrast = if(contrast < 0.5) {
                    contrast / 2.0f
                } else {
                    1.0f - ((1.0f - contrast) / 3.0f)
                }
                it.copy(
                    extended = it.extended.copy(
                        keyboardContainerVariant = it.surfaceTint.copy(alpha = (1.0f - contrast) * 0.12f)
                            .compositeOver(it.surfaceContainerHigh),
                        keyboardContainer = it.surfaceContainerHighest.copy(alpha = contrast)
                            .compositeOver(it.surfaceContainerHigh),
                        keyboardSurface = it.keyboardSurface.copy(alpha = 1.0f - (contrast.pow(4.0f))).compositeOver(Color.Black)
                    )
                )
            }.let {
                it.copy(extended = it.extended.copy(keyRoundness = roundness.floatValue))
            }
        }
    }

    val enableKeyboardPreview = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    var showKeyboard by remember { mutableStateOf(true) }

    if (enableKeyboardPreview) {
        val textInputService = LocalTextInputService.current
        val rootView = (context as? Activity)?.window?.decorView?.rootView
        val session = remember { mutableStateOf<TextInputSession?>(null) }

        DisposableEffect(showKeyboard) {
            val service = textInputService ?: return@DisposableEffect onDispose { }

            if (showKeyboard) {
                session.value = service.startInput(
                    TextFieldValue(""),
                    imeOptions = ImeOptions.Default.copy(
                        platformImeOptions = PlatformImeOptions(
                            privateImeOptions = "org.futo.inputmethod.latin.NoSuggestions=1,org.futo.inputmethod.latin.ThemeMode=1"
                        )
                    ),
                    onEditCommand = { },
                    onImeActionPerformed = { }
                )
            }

            onDispose {
                service.stopInput(session.value ?: return@onDispose)
            }
        }

        // Detect manual keyboard dismissal (e.g., back button press)
        rootView?.let { view ->
            DisposableEffect(view) {
                ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
                    val isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
                    if (!isKeyboardVisible && showKeyboard) {
                        showKeyboard = false
                    }
                    insets
                }

                onDispose {
                    ViewCompat.setOnApplyWindowInsetsListener(view, null)
                }
            }
        }
    }

    LaunchedEffect(scheme) {
        val s = fromKeyboardScheme(scheme).let {
            if(context.customThemePhoto.isFile) {
                val alpha = (1.0f - opacity.floatValue * opacity.floatValue) * 0.32f + 0.5f
                it.copy(
                    backgroundImage = context.customThemePhoto.absolutePath,
                    backgroundImageOpacity = opacity.floatValue,
                    keyboardContainer = it.keyboardContainer.toColor()
                        .copy(alpha = alpha).toSColor(),
                    keyboardContainerVariant = it.keyboardContainerVariant.toColor()
                        .copy(alpha = alpha).toSColor(),
                )
            } else {
                it
            }
        }

        context.setSetting(
            stringPreferencesKey("theme-custom"),
            Json.encodeToString(SerializableCustomTheme.serializer(), s)
        )

        //showKeyboard = false
        if(context.getSetting(THEME_KEY).endsWith("_")) {
            context.setSetting(
                THEME_KEY.key,
                "custom"
            )
        } else {
            context.setSetting(
                THEME_KEY.key,
                "custom_"
            )
        }
        showKeyboard = true
    }

    ScrollableList(spacing=0.dp) {
        SettingToggleRaw("Dark mode", darkMode.value, { darkMode.value = it })
        SettingToggleDataStore(
            title = stringResource(R.string.theme_settings_key_borders),
            setting = KeyBordersSetting
        )

        HctSliderPicker(color.value, { color.value = it })

        SettingSliderForDataStoreItem("Roundness",
            remember(roundness.floatValue) { DataStoreItem(value = roundness.floatValue, setValue = { roundness.floatValue = it; scope.launch { } }) },
            default = roundness.floatValue,
            range = 0.0f .. 2.0f,
            transform = { it },
            steps = 19
        )
        SettingSliderForDataStoreItem("Background Opacity",
            remember(opacity.floatValue) { DataStoreItem(value = opacity.floatValue, setValue = { opacity.floatValue = it; scope.launch { } }) },
            default = opacity.floatValue,
            range = 0.0f .. 1.0f,
            transform = { it },
            steps = 9
        )

        Button(onClick = {
            pickLauncher.launch("image/*")
        }) {
            Text("click to add photo")
        }

        Button(onClick = {
            context.customThemePhoto.delete()
        }) {
            Text("click to remove photo")
        }
    }
}

@Preview
@Composable
fun ThemeScreen(navController: NavHostController = rememberNavController()) {
    val (theme, setTheme) = useDataStore(THEME_KEY.key, THEME_KEY.default)

    val context = LocalContext.current
    val enableKeyboardPreview = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    var showKeyboard by remember { mutableStateOf(false) }

    if (enableKeyboardPreview) {
        val textInputService = LocalTextInputService.current
        val rootView = (context as? Activity)?.window?.decorView?.rootView
        val session = remember { mutableStateOf<TextInputSession?>(null) }

        DisposableEffect(showKeyboard, theme) {
            val service = textInputService ?: return@DisposableEffect onDispose { }

            if (showKeyboard) {
                session.value = service.startInput(
                    TextFieldValue(""),
                    imeOptions = ImeOptions.Default.copy(
                        platformImeOptions = PlatformImeOptions(
                            privateImeOptions = "org.futo.inputmethod.latin.NoSuggestions=1"
                        )
                    ),
                    onEditCommand = { },
                    onImeActionPerformed = { }
                )
            }

            onDispose {
                service.stopInput(session.value ?: return@onDispose)
            }
        }

        // Detect manual keyboard dismissal (e.g., back button press)
        rootView?.let { view ->
            DisposableEffect(view) {
                ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
                    val isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
                    if (!isKeyboardVisible && showKeyboard) {
                        showKeyboard = false
                    }
                    insets
                }

                onDispose {
                    ViewCompat.setOnApplyWindowInsetsListener(view, null)
                }
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            if (enableKeyboardPreview) {
                SmallFloatingActionButton(
                    onClick = {
                        showKeyboard = !showKeyboard
                    }
                ) {
                    RotatingChevronIcon(!showKeyboard)
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Start
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            ScreenTitle(stringResource(R.string.theme_settings_title), showBack = true, navController)
            ThemePicker({
                setTheme(it.key)
            }, {
                navController.navigate("customTheme")
            })
        }
    }
}