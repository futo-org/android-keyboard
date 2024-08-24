package org.futo.inputmethod.v2keyboard

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import org.futo.inputmethod.keyboard.internal.KeyboardParams

object RowKeyListSerializer : SpacedListSerializer<Key>(KeyPathSerializer)

const val NumberRowHeight: Double = 0.8

enum class RowNumberRowMode(val displayByDefault: Boolean, val displayWhenExplicitlyActive: Boolean, val displayWhenExplicitlyInactive: Boolean) {
    Default(true, true, true),
    Filler(false, true, false),
    Hideable(false, false, true),
}

@Serializable
data class Row(
    // Only one of these must be defined
    val numbers: @Serializable(with = RowKeyListSerializer::class) List<Key>? = null,
    val letters: @Serializable(with = RowKeyListSerializer::class) List<Key>? = null,
    val bottom:  @Serializable(with = RowKeyListSerializer::class) List<Key>? = null,

    val rowHeight: Double = if(numbers == null) { 1.0 } else { NumberRowHeight },
    val splittable: Boolean = letters != null,
    val numRowMode: RowNumberRowMode = RowNumberRowMode.Default,
    val attributes: KeyAttributes = KeyAttributes(
        style = when {
            numbers != null -> KeyVisualStyle.NoBackground
            bottom  != null -> KeyVisualStyle.Functional
            else  -> null
        },

        width = when {
            numbers != null -> KeyWidth.Grow
            else  -> null
        },
    )
) {
    val keys: List<Key>
        get() = bottom ?: numbers ?: letters!!

    val isNumberRow: Boolean
        get() = numbers != null

    val isLetterRow: Boolean
        get() = letters != null

    val isBottomRow: Boolean
        get() = bottom != null

    init {

        // Ensure only one is defined
        assert(
            (numbers != null && letters == null && bottom == null)
               || (numbers == null && letters != null && bottom == null)
               || (numbers == null && letters == null && bottom != null)
        ) {
            "Only one of numbers, letters, or actions can be defined per row. In yaml, make sure you did not miss a `-` to start a new row."
        }
    }
}

val DefaultNumberRow = Row(
    numbers = "1234567890".map { BaseKey(it.toString()) }
)

val DefaultBottomRow = Row(
    bottom = listOf(
        TemplateSymbolsKey,
        BaseKey(","),
        TemplateActionKey,
        TemplateSpaceKey,
        TemplateContextualKey,
        BaseKey("."),
        TemplateEnterKey
    )
)

public enum class KeyboardElement {
    Alphabet,
    Symbols,
    SymbolsShifted,
    Phone,
    PhoneSymbols,
    Number
}

enum class NumberRowMode {
    UserConfigurable,
    AlwaysEnabled,
    AlwaysDisabled
}

enum class BottomRowHeightMode {
    Fixed,
    Flexible
}

enum class BottomRowWidthMode(val separateFunctional: Boolean) {
    SeparateFunctional(true),
    Identical(false)
}

enum class RowWidthMode {
    PadSides,
    FillSpace
}

enum class LongPressKeysMode {
    UserConfigurable,
    LayoutOnly
}

enum class RowHeightMode(val clampHeight: Boolean) {
    ClampHeight(true),
    FillHeight(false)
}

object SpacedLanguageListSerializer : SpacedListSerializer<String>(String.serializer(), { it.split(" ") })
typealias SpacedStringList = @Serializable(with = SpacedLanguageListSerializer::class) List<String>

@Serializable
data class Keyboard(
    val name: String,

    @SerialName("rows")
    private val definedRows: List<Row>,

    val description: String = "",
    val languages: SpacedStringList = listOf(),
    val symbolsLayout: String = "symbols",
    val symbolsShiftLayout: String = "symbols_shift",
    val element: KeyboardElement = KeyboardElement.Alphabet,
    val numberRowMode: NumberRowMode = NumberRowMode.UserConfigurable,
    val bottomRowHeightMode: BottomRowHeightMode = BottomRowHeightMode.Fixed,
    val bottomRowWidthMode: BottomRowWidthMode = BottomRowWidthMode.SeparateFunctional,
    val rowWidthMode: RowWidthMode = RowWidthMode.PadSides,
    val script: Script = Script.Latin,
    val longPressKeysMode: LongPressKeysMode = LongPressKeysMode.UserConfigurable,
    val attributes: KeyAttributes = KeyAttributes(),
    val overrideWidths: Map<KeyWidth, Float> = mapOf(),
    val rowHeightMode: RowHeightMode = RowHeightMode.ClampHeight,
    val useZWNJKey: Boolean = false,

    val minimumFunctionalKeyWidth: Float = 0.125f,
    val minimumBottomRowFunctionalKeyWidth: Float = 0.15f,

    val altPages: List<List<Row>> = listOf()
) {
    var id: String = ""

    private fun ensureRowsValid(rows: List<Row>) {
        assert(rows.first().isNumberRow) { "The first row in a keyboard must be the number row" }
        assert(rows.last().isBottomRow)  { "The last row in a keyboard must be the bottom row" }
        assert(rows.count { it.isNumberRow } == 1) { "Keyboard can only contain one number row" }
        assert(rows.count { it.isBottomRow } == 1) { "Keyboard can only contain one bottom row" }
        assert(rows.count { it.isLetterRow } in 1..8) { "Keyboard must contain between 1 and 8 letter rows" }
    }

    val effectiveRows = definedRows.toMutableList().apply {
        if(find { it.isNumberRow } == null) {
            add(0, DefaultNumberRow)
        }

        if(find { it.isBottomRow } == null) {
            // If action row is not explicitly defined, shift and delete are implicitly added to last row
            // (unless they're already there)
            val ultimateRow = removeAt(size - 1)
            assert(ultimateRow.isLetterRow)

            val updatedRow = ultimateRow.copy(
                letters = ultimateRow.letters!!.toMutableList().apply {
                    if(!contains(TemplateShiftKey) && !contains(TemplateDeleteKey)) {
                        add(0, TemplateShiftKey)
                        add(TemplateDeleteKey)
                    }
                }
            )

            add(updatedRow)

            // Add default bottom row
            add(DefaultBottomRow)
        }

        ensureRowsValid(this)
    }.toList()

    fun build(context: Context, params: KeyboardParams, layoutParams: LayoutParams): org.futo.inputmethod.keyboard.Keyboard {
        val engine = LayoutEngine(context, this, params, layoutParams)
        return engine.build()
    }
}