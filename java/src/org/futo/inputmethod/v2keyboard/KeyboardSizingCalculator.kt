package org.futo.inputmethod.v2keyboard

import android.content.Context
import android.graphics.Rect
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.window.layout.FoldingFeature
import org.futo.inputmethod.latin.FoldStateProvider
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.getSettingBlocking
import org.futo.inputmethod.latin.utils.ResourceUtils
import kotlin.math.roundToInt

interface KeyboardSizeStateProvider {
    val currentSizeState: KeyboardSizeSettingKind
}

sealed class ComputedKeyboardSize()

class RegularKeyboardSize(val height: Int, val padding: Rect) : ComputedKeyboardSize()

class SplitKeyboardSize(val height: Int, val padding: Rect, val splitLayoutWidth: Int) : ComputedKeyboardSize()

//class OneHandedKeyboardSize(val height: Int, val offset: Int, val sideInset: Int, val isLeft: Boolean, val width: Int): ComputedKeyboardSize()
//class FloatingKeyboardSize(val x: Int, val y: Int, val width: Int, val height: Int): ComputedKeyboardSize()


enum class KeyboardSizeSettingKind {
    Portrait,
    Landscape,
    FoldableInnerDisplay
}

val SplitKeyboardSettings = mapOf(
    KeyboardSizeSettingKind.Portrait to SettingsKey(
        booleanPreferencesKey("split_keyboard_portrait"), false),
    KeyboardSizeSettingKind.Landscape to SettingsKey(
        booleanPreferencesKey("split_keyboard_landscape"), true),
    KeyboardSizeSettingKind.FoldableInnerDisplay to SettingsKey(
        booleanPreferencesKey("split_keyboard_fold"), true),
)

val KeyboardHeightSettings = mapOf(
    KeyboardSizeSettingKind.Portrait to SettingsKey(
        floatPreferencesKey("keyboardHeightMultiplier"), 1.0f),
    KeyboardSizeSettingKind.Landscape to SettingsKey(
        floatPreferencesKey("keyboard_height_landscape"), 0.9f),
    KeyboardSizeSettingKind.FoldableInnerDisplay to SettingsKey(
        floatPreferencesKey("keyboard_height_fold"), 0.67f),
)

val KeyboardOffsetSettings = mapOf(
    KeyboardSizeSettingKind.Portrait to SettingsKey(
        floatPreferencesKey("keyboard_offset_portrait"), 8.0f),
    KeyboardSizeSettingKind.Landscape to SettingsKey(
        floatPreferencesKey("keyboard_offset_landscape"), 0.0f),
    KeyboardSizeSettingKind.FoldableInnerDisplay to SettingsKey(
        floatPreferencesKey("keyboard_offset_fold"), 8.0f),
)

val KeyboardSideInsetSettings = mapOf(
    KeyboardSizeSettingKind.Portrait to SettingsKey(
        floatPreferencesKey("keyboard_inset_portrait"), 2.0f),
    KeyboardSizeSettingKind.Landscape to SettingsKey(
        floatPreferencesKey("keyboard_inset_landscape"), 8.0f),
    KeyboardSizeSettingKind.FoldableInnerDisplay to SettingsKey(
        floatPreferencesKey("keyboard_inset_fold"), 44.0f),
)


class KeyboardSizingCalculator(val context: Context) {
    private val sizeStateProvider = context as KeyboardSizeStateProvider
    private val foldStateProvider = context as FoldStateProvider

    private fun isSplitKeyboard(mode: KeyboardSizeSettingKind): Boolean =
        context.getSettingBlocking(SplitKeyboardSettings[mode]!!)

    private fun heightMultiplier(mode: KeyboardSizeSettingKind): Float =
        context.getSettingBlocking(KeyboardHeightSettings[mode]!!)

    private fun bottomOffsetPx(mode: KeyboardSizeSettingKind): Int =
        (context.getSettingBlocking(KeyboardOffsetSettings[mode]!!) * context.resources.displayMetrics.density).toInt()

    private fun sideInsetPx(mode: KeyboardSizeSettingKind): Int =
        (context.getSettingBlocking(KeyboardSideInsetSettings[mode]!!) * context.resources.displayMetrics.density).toInt()

    private fun topPaddingPx(mode: KeyboardSizeSettingKind): Int =
        (when(mode) {
            KeyboardSizeSettingKind.Portrait -> 4.0f
            KeyboardSizeSettingKind.Landscape -> 0.0f
            KeyboardSizeSettingKind.FoldableInnerDisplay -> 8.0f
        } * context.resources.displayMetrics.density).toInt()

    fun calculate(layoutName: String, isNumberRowActive: Boolean): ComputedKeyboardSize {
        val layout = LayoutManager.getLayout(context, layoutName)
        val effectiveRowCount = layout.effectiveRows.size

        val configuration = context.resources.configuration
        val displayMetrics = context.resources.displayMetrics

        val mode = sizeStateProvider.currentSizeState

        val isSplit = isSplitKeyboard(mode)
        val heightMultiplier = heightMultiplier(mode)
        val bottomOffset = bottomOffsetPx(mode)
        val sideInset = sideInsetPx(mode)
        val topPadding = topPaddingPx(mode)

        val singularRowHeight = (ResourceUtils.getDefaultKeyboardHeight(context.resources) / 4.0) * heightMultiplier

        val numRows = 4.0 +
                ((effectiveRowCount - 5) / 2.0).coerceAtLeast(0.0) +
                if(isNumberRowActive) { 0.5 } else { 0.0 }

        println("Num rows; $numRows, $effectiveRowCount ($layoutName) ($layout)")

        val recommendedHeight = numRows * singularRowHeight


        val foldState = foldStateProvider.foldState.feature

        return when {
            // Special case: 50% screen height no matter the row count or settings
            foldState != null && foldState.state == FoldingFeature.State.HALF_OPENED && foldState.orientation == FoldingFeature.Orientation.HORIZONTAL ->
                SplitKeyboardSize(
                    displayMetrics.heightPixels / 2 - (displayMetrics.density * 80.0f).toInt(),
                    Rect(
                        (displayMetrics.density * 44.0f).roundToInt(),
                        (displayMetrics.density * 20.0f).roundToInt(),
                        (displayMetrics.density * 44.0f).roundToInt(),
                        (displayMetrics.density * 12.0f).roundToInt(),
                    ),
                    displayMetrics.widthPixels * 3 / 5
                )

            isSplit -> SplitKeyboardSize(
                recommendedHeight.roundToInt(),
                Rect(sideInset, topPadding, sideInset, bottomOffset),
                displayMetrics.widthPixels * 3 / 5)

            else -> RegularKeyboardSize(
                recommendedHeight.roundToInt(),
                Rect(sideInset, topPadding, sideInset, bottomOffset),
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
}