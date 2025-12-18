package org.futo.inputmethod.latin.uix.settings.pages.themes

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.ContextThemeWrapper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.graphics.scale
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.RichInputMethodManager
import org.futo.inputmethod.latin.SuggestedWords
import org.futo.inputmethod.latin.uix.ActionBar
import org.futo.inputmethod.latin.uix.BasicThemeProvider
import org.futo.inputmethod.latin.uix.DynamicThemeProvider
import org.futo.inputmethod.latin.uix.DynamicThemeProviderOwner
import org.futo.inputmethod.latin.uix.ExampleListener
import org.futo.inputmethod.latin.uix.KeyboardLayoutPreview
import org.futo.inputmethod.latin.uix.LocalKeyboardScheme
import org.futo.inputmethod.latin.uix.LocalThemeProvider
import org.futo.inputmethod.latin.uix.THEME_KEY
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.setSetting
import org.futo.inputmethod.latin.uix.settings.DataStoreItem
import org.futo.inputmethod.latin.uix.settings.SettingSliderForDataStoreItem
import org.futo.inputmethod.latin.uix.theme.CustomThemeBuilderConfiguration
import org.futo.inputmethod.latin.uix.theme.ZipThemes
import org.futo.inputmethod.latin.uix.theme.ThemeDecodingContext
import org.futo.inputmethod.latin.uix.theme.presets.DefaultDarkScheme
import org.futo.inputmethod.v2keyboard.LayoutManager
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

private data class Tool(
    val name: Int,
    val icon: Int,
    val isActive: (() -> Boolean)? = null,
    val tool: (@Composable () -> Unit)? = null,
    val press: (() -> Unit)? = null,

)

const val fixedAspectRatio = 1.2f / 1.0f

const val computeDelay = 0L //100L

private fun limitBitmapSize(context: Context, bitmap: Bitmap): Bitmap {
    val maxDimension = maxOf(
        context.resources.displayMetrics.widthPixels,
        context.resources.displayMetrics.heightPixels
    ) * 3 / 2
    
    val imgMaxDimension = maxOf(
        bitmap.width,
        bitmap.height
    )

    val scale = maxDimension.toFloat() / imgMaxDimension.toFloat()
    if(scale < 1.0f) {
        return bitmap.scale(
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1))
    } else {
        return bitmap
    }
}

// TODO: Break this up into smaller composables
@OptIn(ExperimentalLayoutApi::class)
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
@Preview(showBackground = true)
internal fun ThemeEditor(
    navController: NavHostController = rememberNavController(),
    name: String = "0",
    startingBitmap: Bitmap? = null,
) {
    val context = LocalContext.current
    val ensureInitialized = remember {
        LayoutManager.init(context)
        RichInputMethodManager.init(context)
        true
    }

    val originalBitmap = remember {
        (startingBitmap ?: BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher_keyboard)).let {
            limitBitmapSize(context, it)
        }
    }
    val startingColor = remember {
        extractMainColor(originalBitmap)
    }

    var blurLevel by remember { mutableFloatStateOf(0.0f) }
    var bitmap by rememberDelayedRecomputed(blurLevel, originalBitmap, delay=computeDelay) {
        if(blurLevel == 0.0f) {
            originalBitmap.asImageBitmap()
        } else {
            applyBlur(context, originalBitmap, blurLevel).asImageBitmap()
        }
    }

    val thumbnailScale = remember {
        {
            minOf(384f / bitmap.width, 384f / bitmap.height, 1.0f)
        }
    }

    var cropRect by remember {
        mutableStateOf(
            if (originalBitmap.height * fixedAspectRatio > originalBitmap.width) {
                val newHeight = originalBitmap.width / fixedAspectRatio
                val top = originalBitmap.height / 2.0f - newHeight / 2.0f
                Rect(
                    0f,
                    top,
                    originalBitmap.width.toFloat(),
                    top + newHeight
                )
            } else {
                val newWidth = originalBitmap.height * fixedAspectRatio
                val left = originalBitmap.width / 2.0f - newWidth / 2.0f
                Rect(
                    left,
                    0f,
                    left + newWidth,
                    originalBitmap.height.toFloat()
                )
            }
        )
    }

    var areaSize by remember { mutableStateOf(IntSize(0, 0)) }
    var opacity by remember { mutableFloatStateOf(0.7f) }

    var darkMode by remember { mutableStateOf(startingColor.tone < 50f) }

    var borders by remember { mutableStateOf(false) }

    var color by remember { mutableStateOf(Hct2(startingColor.hue, startingColor.chroma, 0f)) }

    val themeCtx = remember {
        object : ThemeDecodingContext {
            override val context: Context
                get() = context

            override fun getFileBytes(path: String): ByteArray? = when {
                path == "background.jpg" -> {
                    val stream = ByteArrayOutputStream()
                    bitmap.asAndroidBitmap().compress(
                        Bitmap.CompressFormat.JPEG,
                        90,
                        stream
                    )
                    stream.toByteArray()
                }

                path == "thumbnail.jpg" -> {
                    val stream = ByteArrayOutputStream()
                    val scale = thumbnailScale()
                    val scaled = Bitmap.createScaledBitmap(
                        bitmap.asAndroidBitmap(),
                        (bitmap.width * scale).toInt().coerceAtLeast(1),
                        (bitmap.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                    scaled.compress(
                        Bitmap.CompressFormat.JPEG,
                        90,
                        stream
                    )
                    stream.toByteArray()
                }

                else -> null
            }

            override fun getFileHash(path: String): String? = null
            override fun close() {
                TODO("Not yet implemented")
            }

        }
    }

    val themeCfg by rememberDelayedRecomputed(color, opacity, darkMode, borders, delay=computeDelay) {
        CustomThemeBuilderConfiguration(
            hue = color.hue.toDouble(),
            chroma = color.chroma.toDouble(),
            contrast = color.tone / 100f,
            darkMode = darkMode,
            borders = borders,
            // we don't specify it here to skip constantly reloading image
            backgroundImagePath = "",
            backgroundImageOpacity = opacity
        )
    }

    val theme by rememberDelayedRecomputed(themeCfg, delay=computeDelay) {
        themeCfg.build().toKeyboardScheme(themeCtx).let {
            // BackgroundImage is checked by ActionBar, we set it here
            it.copy(extended = it.extended.let {
                it.copy(advancedThemeOptions = it.advancedThemeOptions.copy(
                    backgroundImage = bitmap
                ))
            })
        }
    }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        var r = cropRect
        val canvasScale = r.width.toFloat() / areaSize.width.toFloat()

        r = r.copy(
            left = r.left - panChange.x * canvasScale,
            right = r.right - panChange.x * canvasScale,
            top = r.top - panChange.y * canvasScale,
            bottom = r.bottom - panChange.y * canvasScale
        )

        if (zoomChange != 1.0f) {
            var newWidth = r.width / zoomChange
            var newHeight = r.height / zoomChange

            if (minOf(newWidth, newHeight) < 8.0f) {
                newWidth = 8.0f * fixedAspectRatio
                newHeight = 8.0f
            }
            if (newWidth > originalBitmap.width) {
                newWidth = originalBitmap.width.toFloat()
                newHeight = newWidth / fixedAspectRatio
            }
            if (newHeight > originalBitmap.height) {
                newHeight = originalBitmap.height.toFloat()
                newWidth = newHeight * fixedAspectRatio
            }
            r = r.copy(
                left = r.center.x - newWidth / 2,
                right = r.center.x + newWidth / 2,
                top = r.center.y - newHeight / 2,
                bottom = r.center.y + newHeight / 2,
            )
        }

        if (r.left < 0.0f) r = r.copy(left = 0.0f, right = r.right - r.left)
        if (r.top < 0.0f) r = r.copy(top = 0.0f, bottom = r.bottom - r.top)

        val iw = originalBitmap.width.toFloat()
        val ih = originalBitmap.height.toFloat()
        if (r.right > iw) r = r.copy(left = r.left - (r.right - iw), right = iw)
        if (r.bottom > ih) r = r.copy(top = r.top - (r.bottom - ih), bottom = ih)

        cropRect = r
    }


    var themeProvider = remember { mutableStateOf(BasicThemeProvider(context, theme)) }
    LaunchedEffect(theme) {
        themeProvider.value = BasicThemeProvider(context, theme)
    }

    val customThemeCtx = remember {
        object : ContextThemeWrapper(context, R.style.KeyboardTheme_LXX_Light),
            DynamicThemeProviderOwner {
            override fun getDrawableProvider(): DynamicThemeProvider = themeProvider.value
        }
    }

    val tools = remember {
        listOf(
            Tool(
                R.string.theme_customizer_transparency_slider_name, R.drawable.transparency,
                tool = @Composable {
                    SettingSliderForDataStoreItem(
                        stringResource(R.string.theme_customizer_transparency_slider_name),
                        remember(opacity) {
                            DataStoreItem(
                                value = opacity,
                                setValue = { opacity = it }
                            )
                        },
                        default = opacity,
                        range = 0.0f..1.0f,
                        transform = { it },
                        indicator = { "${(it * 100f).roundToInt()}%" },
                        steps = 9
                    )
                }),

            Tool(
                R.string.theme_customizer_blur_slider_name, R.drawable.blur,
                tool = @Composable {
                    SettingSliderForDataStoreItem(
                        stringResource(R.string.theme_customizer_blur_slider_name),
                        remember(blurLevel) {
                            DataStoreItem(
                                value = blurLevel,
                                setValue = { blurLevel = it }
                            )
                        },
                        default = blurLevel,
                        range = 0.0f..1.0f,
                        transform = { it },
                        indicator = { "${(it * 100f).roundToInt()}%" },
                        steps = 9
                    )
                }
            ),

            Tool(
                R.string.theme_customizer_color_sliders_name, R.drawable.themes,
                tool = @Composable {
                    HctSliderPicker(color) {
                        color = it
                    }
                }
            ),

            Tool(
                R.string.theme_customizer_dark_mode_toggle, R.drawable.moon,
                isActive = { darkMode },
                press = { darkMode = !darkMode }
            ),

            Tool(
                R.string.theme_settings_key_borders, R.drawable.key_border,
                isActive = { borders },
                press = { borders = !borders }
            )
        )
    }

    var activeTool by remember { mutableStateOf<Tool?>(tools.first()) }

    val lifecycle = LocalLifecycleOwner.current
    var saving by remember { mutableStateOf(false) }
    val finish = {
        saving = true
        lifecycle.lifecycleScope.launch {
            withContext(Dispatchers.Default) {
                val bitmapScale = originalBitmap.width.toFloat() / bitmap.width.toFloat()
                ZipThemes.save(
                    themeCtx,
                    themeCfg.copy(
                        backgroundImagePath = "background.jpg",
                        backgroundImageRect = Rect(
                            left = cropRect.left / bitmapScale,
                            top = cropRect.top / bitmapScale,
                            right = cropRect.right / bitmapScale,
                            bottom = cropRect.bottom / bitmapScale,
                        ),
                        thumbnailImagePath = "thumbnail.jpg",
                        thumbnailImageScale = thumbnailScale()
                    ).build(),
                    ZipThemes.custom(name)
                )
            }
            withContext(Dispatchers.Main) {
                if (context.getSetting(THEME_KEY).endsWith("_")) {
                    context.setSetting(
                        THEME_KEY.key,
                        "custom$name"
                    )
                } else {
                    context.setSetting(
                        THEME_KEY.key,
                        "custom${name}_"
                    )
                }

                navController.navigateUp()
            }
        }
        Unit
    }

    val colorsForEditor = remember { DefaultDarkScheme.obtainColors(context) }
    Box(
        modifier = Modifier.Companion
            .fillMaxSize()
            .background(Color.Companion.Black),
        contentAlignment = Alignment.Companion.Center
    ) {
        MaterialTheme(colorScheme = colorsForEditor.base) {
            CompositionLocalProvider(LocalContentColor provides Color.Companion.White) {
                Column(
                    modifier = Modifier.Companion.padding(16.dp),
                    horizontalAlignment = Alignment.Companion.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        Modifier.Companion.weight(1.0f).fillMaxWidth()
                            .transformable(transformableState),
                        contentAlignment = Alignment.Companion.Center
                    ) {
                        Box(
                            Modifier.Companion
                                .aspectRatio(fixedAspectRatio, false)
                                .onSizeChanged { areaSize = it }
                                .zIndex(-1f),
                            contentAlignment = Alignment.Companion.Center
                        ) {
                            val radiusDp = 8.dp
                            val radius =
                                CornerRadius(with(LocalDensity.current) { radiusDp.toPx() })
                            Canvas(Modifier.Companion.matchParentSize()) {
                                val canvasScale = size.width / cropRect.width
                                val bitmapScale =
                                    originalBitmap.width.toFloat() / bitmap.width.toFloat()

                                val offset = Offset(
                                    -cropRect.left,
                                    -cropRect.top
                                )

                                scale(canvasScale, pivot = Offset.Companion.Zero) {
                                    translate(offset.x, offset.y) {
                                        scale(bitmapScale, pivot = Offset.Companion.Zero) {
                                            drawImage(image = bitmap)
                                            drawRect(
                                                Color.Companion.Black.copy(alpha = 0.8f),
                                                size = Size(
                                                    bitmap.width.toFloat(),
                                                    bitmap.height.toFloat()
                                                ),
                                            )
                                        }
                                    }
                                }

                                withTransform({
                                    clipPath(Path().apply {
                                        addRoundRect(
                                            RoundRect(
                                                left = 0f, top = 0f,
                                                right = size.width, bottom = size.height,
                                                cornerRadius = radius
                                            )
                                        )
                                    })
                                }) {
                                    scale(canvasScale, pivot = Offset.Companion.Zero) {
                                        translate(offset.x, offset.y) {
                                            scale(bitmapScale, pivot = Offset.Companion.Zero) {
                                                drawImage(
                                                    image = bitmap,
                                                )
                                            }
                                        }
                                    }
                                }

                                drawRoundRect(
                                    color = Color.Companion.Black,
                                    cornerRadius = radius,
                                    style = Stroke(
                                        width = 2f,
                                        pathEffect = PathEffect.Companion.dashPathEffect(
                                            floatArrayOf(10f, 10f),
                                            0f
                                        )
                                    )
                                )
                                drawRoundRect(
                                    color = Color.Companion.White,
                                    cornerRadius = radius,
                                    style = Stroke(
                                        width = 2f,
                                        pathEffect = PathEffect.Companion.dashPathEffect(
                                            floatArrayOf(10f, 10f),
                                            10f
                                        )
                                    )
                                )
                            }
                            if (areaSize.width > 0) {
                                Box(
                                    Modifier.Companion.matchParentSize()
                                        .clip(RoundedCornerShape(radiusDp))
                                ) {
                                    theme.keyboardBackgroundGradient?.let {
                                        Box(Modifier.Companion.matchParentSize().background(it))
                                    }

                                    Column {
                                        CompositionLocalProvider(
                                            LocalThemeProvider provides themeProvider.value,
                                            LocalKeyboardScheme provides theme
                                        ) {
                                            val suggestedWords = remember {
                                                val suggestedWordsForActionBar = arrayListOf(
                                                    SuggestedWords.SuggestedWordInfo(
                                                        context.getString(R.string.theme_customizer_adjust_background_image_hint),
                                                        "",
                                                        100,
                                                        1,
                                                        null,
                                                        0,
                                                        0
                                                    ),
                                                )
                                                SuggestedWords(
                                                    suggestedWordsForActionBar,
                                                    suggestedWordsForActionBar,
                                                    suggestedWordsForActionBar[0],
                                                    true,
                                                    true,
                                                    false,
                                                    0,
                                                    0
                                                )
                                            }
                                            ActionBar(
                                                words = suggestedWords,
                                                suggestionStripListener = ExampleListener(),
                                                onActionActivated = { },
                                                inlineSuggestions = listOf(),
                                                isActionsExpanded = false,
                                                toggleActionsExpanded = { },
                                                onActionAltActivated = { }
                                            )
                                            Spacer(Modifier.Companion.height(4.dp))
                                        }
                                        KeyboardLayoutPreview(
                                            RichInputMethodManager.getInstance().currentSubtype.keyboardLayoutSetName,
                                            width = with(LocalDensity.current) {
                                                areaSize.width.toDp()
                                            }, customThemeCtx = customThemeCtx
                                        )
                                    }
                                }
                            }
                        }
                    }

                    activeTool?.tool?.invoke()

                    FlowRow(Modifier.background(Color.Black, RoundedCornerShape(16.dp)).padding(8.dp).height(48.dp)) {
                        var toolEndReached = false
                        tools.forEach {
                            if(it.tool == null && !toolEndReached) {
                                VerticalDivider(color = Color.DarkGray, modifier = Modifier.padding(8.dp))
                                toolEndReached = true
                            }

                            val active = it.isActive?.invoke() == true || activeTool==it
                            IconButton(onClick = {
                                if (it.tool != null) activeTool = it
                                else it.press?.invoke()
                            }, colors= IconButtonDefaults.iconButtonColors(Color.Black, if(active) Color.White else Color.Gray )) {
                                Icon(painterResource(it.icon), contentDescription=stringResource(it.name))
                            }
                        }
                    }
                    if (!saving) {
                        Button(onClick = finish) { Text(stringResource(R.string.theme_customizer_finish_theme_adjustment)) }
                    } else {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}