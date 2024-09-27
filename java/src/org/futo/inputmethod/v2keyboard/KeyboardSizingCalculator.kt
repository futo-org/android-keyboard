package org.futo.inputmethod.v2keyboard

import android.content.Context
import android.graphics.Rect
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.window.layout.FoldingFeature
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.Json
import org.futo.inputmethod.latin.FoldStateProvider
import org.futo.inputmethod.latin.LatinIME
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.UixManager
import org.futo.inputmethod.latin.uix.getSettingBlocking
import org.futo.inputmethod.latin.uix.setSettingBlocking
import org.futo.inputmethod.latin.utils.ResourceUtils
import kotlin.math.roundToInt

interface KeyboardSizeStateProvider {
    val currentSizeState: KeyboardSizeSettingKind
}

sealed class ComputedKeyboardSize()

class RegularKeyboardSize(val height: Int, val width: Int, val padding: Rect) : ComputedKeyboardSize()

class SplitKeyboardSize(val height: Int, val width: Int, val padding: Rect, val splitLayoutWidth: Int) : ComputedKeyboardSize()

enum class OneHandedDirection {
    Left,
    Right
}

class OneHandedKeyboardSize(val height: Int, val width: Int, val padding: Rect, val layoutWidth: Int, val direction: OneHandedDirection): ComputedKeyboardSize()

class FloatingKeyboardSize(
    val bottomOrigin: Pair<Int, Int>,
    val width: Int,
    val height: Int,
    val decorationPadding: Rect
): ComputedKeyboardSize()

fun ComputedKeyboardSize.getHeight(): Int = when(this) {
    is FloatingKeyboardSize -> height
    is OneHandedKeyboardSize -> height
    is RegularKeyboardSize -> height
    is SplitKeyboardSize -> height
}

fun ComputedKeyboardSize.getWidth(): Int = when(this) {
    is FloatingKeyboardSize -> width
    is OneHandedKeyboardSize -> width
    is RegularKeyboardSize -> width
    is SplitKeyboardSize -> width
}

fun ComputedKeyboardSize.getPadding(): Rect = when(this) {
    is FloatingKeyboardSize -> decorationPadding
    is OneHandedKeyboardSize -> padding
    is RegularKeyboardSize -> padding
    is SplitKeyboardSize -> padding
}

fun ComputedKeyboardSize.getTotalKeyboardWidth(): Int = when(this) {
    is FloatingKeyboardSize -> width - decorationPadding.left - decorationPadding.right
    is OneHandedKeyboardSize -> layoutWidth
    is RegularKeyboardSize -> width - padding.left - padding.right
    is SplitKeyboardSize -> width - padding.left - padding.right
}

enum class KeyboardMode {
    Regular,
    Split,
    OneHanded,
    Floating
}



@Serializer(forClass = Rect::class)
object RectSerializer : KSerializer<Rect> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Rect") {
        element<Int>("left")
        element<Int>("top")
        element<Int>("right")
        element<Int>("bottom")
    }

    override fun serialize(encoder: Encoder, value: Rect) {
        encoder.encodeStructure(descriptor) {
            encodeIntElement(descriptor, 0, value.left)
            encodeIntElement(descriptor, 1, value.top)
            encodeIntElement(descriptor, 2, value.right)
            encodeIntElement(descriptor, 3, value.bottom)
        }
    }

    override fun deserialize(decoder: Decoder): Rect {
        return decoder.decodeStructure(descriptor) {
            var left = 0
            var top = 0
            var right = 0
            var bottom = 0

            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> left = decodeIntElement(descriptor, 0)
                    1 -> top = decodeIntElement(descriptor, 1)
                    2 -> right = decodeIntElement(descriptor, 2)
                    3 -> bottom = decodeIntElement(descriptor, 3)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }

            Rect(left, top, right, bottom)
        }
    }
}



@Serializable
data class SavedKeyboardSizingSettings(
    val currentMode: KeyboardMode,
    val heightMultiplier: Float,
    val paddingDp: @Serializable(RectSerializer::class) Rect,

    // Split
    val splitWidthFraction: Float,

    // One handed, values with respect to left handed mode
    // left = padding
    // right = width + padding
    // bottom = padding for bottom
    val oneHandedRectDp: @Serializable(RectSerializer::class) Rect,
    val oneHandedDirection: OneHandedDirection,

    // Floating
    // bottom left of the floating keyboard, relative to bottom left of screen, .second is Y up
    val floatingBottomOriginDp: Pair<Float, Float>,
    val floatingWidthDp: Float,
    val floatingHeightDp: Float
) {
    fun toJsonString(): String =
        Json.encodeToString(this)

    companion object {
        @JvmStatic
        fun fromJsonString(s: String): SavedKeyboardSizingSettings? =
            try {
                Json.decodeFromString(s)
            } catch (e: Exception) {
                //e.printStackTrace()
                null
            }
    }
}

enum class KeyboardSizeSettingKind {
    Portrait,
    Landscape,
    FoldableInnerDisplay
}

val DefaultKeyboardSettings = mapOf(
    KeyboardSizeSettingKind.Portrait to SavedKeyboardSizingSettings(
        currentMode = KeyboardMode.Regular,
        heightMultiplier = 1.0f,
        paddingDp = Rect(2, 4, 2, 10),
        splitWidthFraction = 4.0f / 5.0f,
        oneHandedDirection = OneHandedDirection.Right,
        oneHandedRectDp = Rect(4, 4, 364, 30),
        floatingBottomOriginDp = Pair(0.0f, 0.0f),
        floatingHeightDp = 240.0f,
        floatingWidthDp = 360.0f
    ),

    KeyboardSizeSettingKind.Landscape to SavedKeyboardSizingSettings(
        currentMode = KeyboardMode.Split,
        heightMultiplier = 0.9f,
        paddingDp = Rect(8, 2, 8, 2),
        splitWidthFraction = 3.0f / 5.0f,
        oneHandedDirection = OneHandedDirection.Right,
        oneHandedRectDp = Rect(4, 4, 364, 30),
        floatingBottomOriginDp = Pair(0.0f, 0.0f),
        floatingHeightDp = 240.0f,
        floatingWidthDp = 360.0f
    ),

    KeyboardSizeSettingKind.FoldableInnerDisplay to SavedKeyboardSizingSettings(
        currentMode = KeyboardMode.Split,
        heightMultiplier = 0.67f,
        paddingDp = Rect(44, 4, 44, 8),
        splitWidthFraction = 3.0f / 5.0f,
        oneHandedDirection = OneHandedDirection.Right,
        oneHandedRectDp = Rect(4, 4, 364, 30),
        floatingBottomOriginDp = Pair(0.0f, 0.0f),
        floatingHeightDp = 240.0f,
        floatingWidthDp = 360.0f
    ),
)

val KeyboardSettings = mapOf(
    KeyboardSizeSettingKind.Portrait to SettingsKey(
        stringPreferencesKey("keyboard_settings_portrait"), ""),
    KeyboardSizeSettingKind.Landscape to SettingsKey(
        stringPreferencesKey("keyboard_settings_landscape"), ""),
    KeyboardSizeSettingKind.FoldableInnerDisplay to SettingsKey(
        stringPreferencesKey("keyboard_settings_fold"), ""),
)

class KeyboardSizingCalculator(val context: Context, val uixManager: UixManager) {
    val sizeStateProvider = context as KeyboardSizeStateProvider
    val foldStateProvider = context as FoldStateProvider

    private fun dp(v: Number): Int =
        (v.toFloat() * context.resources.displayMetrics.density).toInt()

    private fun dp(v: Rect): Rect =
        Rect(dp(v.left), dp(v.top), dp(v.right), dp(v.bottom))

    private fun limitFloating(rectPx: Rect): Rect {
        val width = rectPx.width()
        val height = rectPx.height()

        val minWidth = dp(160)
        val minHeight = dp(160)

        if(width < minWidth) {
            val delta = minWidth - width
            rectPx.left -= delta / 2
            rectPx.right += delta / 2
        }

        if(height < minHeight) {
            val delta = minHeight - height
            rectPx.top -= delta
            rectPx.bottom += delta
        }

        val maxWidth = context.resources.displayMetrics.widthPixels * 2 / 3
        val maxHeight = context.resources.displayMetrics.heightPixels * 2 / 3

        if(width > maxWidth) {
            val delta = width - maxWidth
            rectPx.left += delta / 2
            rectPx.right -= delta / 2
        }

        if(height > maxHeight) {
            val delta = height - maxHeight
            rectPx.top += delta / 2
            rectPx.bottom -= delta / 2
        }


        val originX = rectPx.left
        val originY = rectPx.top

        if(originX < 0){
            rectPx.left -= originX
            rectPx.right -= originX
        }

        if(originY < 0) {
            rectPx.top -= originY
            rectPx.bottom -= originY
        }

        if(rectPx.right > context.resources.displayMetrics.widthPixels) {
            val delta = rectPx.right - context.resources.displayMetrics.widthPixels
            rectPx.right -= delta
            rectPx.left -= delta
        }
        if(rectPx.bottom < 0) {
            val delta = rectPx.bottom
            rectPx.top -= delta
            rectPx.bottom -= delta
        }

        return rectPx
    }

    fun getSavedSettings(): SavedKeyboardSizingSettings =
        SavedKeyboardSizingSettings.fromJsonString(context.getSettingBlocking(
            KeyboardSettings[sizeStateProvider.currentSizeState]!!
        )) ?: DefaultKeyboardSettings[sizeStateProvider.currentSizeState]!!

    fun editSavedSettings(transform: (SavedKeyboardSizingSettings) -> SavedKeyboardSizingSettings) {
        val sizeState = sizeStateProvider.currentSizeState

        val savedSettings = SavedKeyboardSizingSettings.fromJsonString(context.getSettingBlocking(
            KeyboardSettings[sizeState]!!
        )) ?: DefaultKeyboardSettings[sizeState]!!

        val transformed = transform(savedSettings)

        if(transformed != savedSettings) {
            context.setSettingBlocking(KeyboardSettings[sizeState]!!.key, transformed.toJsonString())
        }
    }

    fun calculate(layoutName: String, isNumberRowActive: Boolean): ComputedKeyboardSize {
        val savedSettings = getSavedSettings()

        val layout = LayoutManager.getLayout(context, layoutName)
        val effectiveRowCount = layout.effectiveRows.size

        val displayMetrics = context.resources.displayMetrics

        val singularRowHeight = (ResourceUtils.getDefaultKeyboardHeight(context.resources) / 4.0) *
                savedSettings.heightMultiplier

        val numRows = 4.0 +
                ((effectiveRowCount - 5) / 2.0).coerceAtLeast(0.0) +
                if(isNumberRowActive) { 0.5 } else { 0.0 }

        val recommendedHeight = numRows * singularRowHeight

        val foldState = foldStateProvider.foldState.feature

        val window = (context as LatinIME).window.window
        val width = ResourceUtils.getDefaultKeyboardWidth(window, context.resources)

        return when {
            // Special case: 50% screen height no matter the row count or settings
            foldState != null && foldState.state == FoldingFeature.State.HALF_OPENED && foldState.orientation == FoldingFeature.Orientation.HORIZONTAL ->
                SplitKeyboardSize(
                    height = displayMetrics.heightPixels / 2 - (displayMetrics.density * 80.0f).toInt(),
                    width = width,
                    padding = Rect(
                        (displayMetrics.density * 44.0f).roundToInt(),
                        (displayMetrics.density * 50.0f).roundToInt(),
                        (displayMetrics.density * 44.0f).roundToInt(),
                        (displayMetrics.density * 12.0f).roundToInt(),
                    ),
                    splitLayoutWidth = displayMetrics.widthPixels * 3 / 5
                )

            savedSettings.currentMode == KeyboardMode.Split ->
                SplitKeyboardSize(
                    height = recommendedHeight.roundToInt(),
                    width = width,
                    padding = dp(savedSettings.paddingDp),
                    splitLayoutWidth = (displayMetrics.widthPixels * savedSettings.splitWidthFraction).toInt()
                )

            savedSettings.currentMode == KeyboardMode.OneHanded ->
                OneHandedKeyboardSize(
                    height = recommendedHeight.roundToInt(),
                    width = width,
                    padding = dp(savedSettings.oneHandedRectDp).let { rect ->
                        when(savedSettings.oneHandedDirection) {
                            OneHandedDirection.Left -> Rect(rect.left, rect.top, rect.left, rect.bottom)
                            OneHandedDirection.Right -> Rect(rect.left, rect.top, rect.left, rect.bottom)
                        }
                    },
                    layoutWidth = dp(savedSettings.oneHandedRectDp.width()).coerceAtMost(displayMetrics.widthPixels * 9 / 10),
                    direction = savedSettings.oneHandedDirection
                )

            savedSettings.currentMode == KeyboardMode.Floating -> {
                val singularRowHeightFloat = dp(savedSettings.floatingHeightDp) / 4.0f
                val recommendedHeightFloat = singularRowHeightFloat * numRows
                FloatingKeyboardSize(
                    bottomOrigin = Pair(
                        dp(savedSettings.floatingBottomOriginDp.first),
                        dp(savedSettings.floatingBottomOriginDp.second)
                    ),
                    width = dp(savedSettings.floatingWidthDp),
                    height = recommendedHeightFloat.toInt(),
                    decorationPadding = dp(
                        Rect(
                            8,
                            8,
                            8,
                            8
                        )
                    )
                )
            }

            else ->
                RegularKeyboardSize(
                    height = recommendedHeight.roundToInt(),
                    width = width,
                    padding = dp(savedSettings.paddingDp)
                )
        }
    }

    fun calculateGap(): Float {
        val displayMetrics = context.resources.displayMetrics

        val widthDp = displayMetrics.widthPixels / displayMetrics.density
        val heightDp = displayMetrics.heightPixels / displayMetrics.density

        val minDp = Math.min(widthDp, heightDp)

        return (minDp / 100.0f).coerceIn(3.0f, 6.0f)
    }

    fun calculateSuggestionBarHeightDp(): Float {
        return 40.0f
    }

    fun calculateTotalActionBarHeightPx(): Int =
        when {
            uixManager.actionsExpanded -> dp(2 * calculateSuggestionBarHeightDp())
            else -> dp(calculateSuggestionBarHeightDp())
        }
}