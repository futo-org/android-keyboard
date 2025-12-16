package org.futo.inputmethod.v2keyboard

import android.content.Context
import android.graphics.Rect
import android.util.Log
import androidx.compose.ui.unit.Dp
import org.futo.inputmethod.keyboard.KeyConsts
import org.futo.inputmethod.keyboard.internal.KeyboardLayoutElement
import org.futo.inputmethod.keyboard.internal.KeyboardParams
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.uix.DynamicThemeProvider
import kotlin.math.floor
import kotlin.math.roundToInt

val EPS = 1e-5.toFloat()

// Entries are either a key, or a gap.
// Gaps are added if the row is mostly regular-width keys and there are fewer keys in this row
// e.g.
// q w e r t y u i o p
// _a s d f g h j k l_
// ___z x c v b n m___
// ^^^ gap     gap ^^^
sealed class LayoutEntry(val widthPx: Float) {
    class Key(val data: ComputedKeyData, widthPx: Float) : LayoutEntry(widthPx)
    class Gap(widthPx: Float): LayoutEntry(widthPx)
}

// Split a row into two, for split keyboard layouts.
// If there is an even number of entries, it will be split in the middle
// If there is an odd number, the middle entry will be duplicated
fun List<LayoutEntry>.splitRow(): Pair<List<LayoutEntry>, List<LayoutEntry>> {
    val row0 = subList(0, (size / 2.0f).roundToInt() )
    val row1 = subList(floor(size / 2.0f).roundToInt(), size)

    return Pair(row0, row1)
}

// Adds gaps, keeping anchored keys anchored.
// If only one side has an anchored key, the entire gap will be added
// to the side without an anchored key
fun List<LayoutEntry.Key>.addGap(totalGap: Float): List<LayoutEntry> {
    if(totalGap == 0.0f) {
        return this
    } else {
        val firstGapIdx = indexOfFirst { !it.data.anchored }.let {
            if(it == -1) 0 else it
        }
        val finalGapIdx = indexOfLast { !it.data.anchored }.let {
            if(it == -1) size - 1 else it
        }

        if(firstGapIdx == -1) return this

        val hasAnchorLeft = firstGapIdx != 0
        val hasAnchorRight = finalGapIdx != size - 1

        return filterIsInstance<LayoutEntry>().toMutableList().apply {
            var gapLeft =  totalGap / 2.0f
            var gapRight = totalGap / 2.0f

            if(hasAnchorLeft && !hasAnchorRight) {
                gapLeft = 0.0f
                gapRight = totalGap
            } else if(hasAnchorRight && !hasAnchorLeft) {
                gapLeft = totalGap
                gapRight = 0.0f
            }

            add(firstGapIdx, LayoutEntry.Gap(gapLeft))
            add(finalGapIdx + 2, LayoutEntry.Gap(gapRight))
        }
    }
}

data class LayoutRow(
    val entries: List<LayoutEntry>,
    val widths: Map<KeyWidth, Float>,
    val height: Float,
    val splittable: Boolean
)

val ArrowRow = Row(
    letters = listOf(
        BaseKey("!icon/action_up|!code/action_up"),
        BaseKey("!icon/action_down|!code/action_down"),
        BaseKey("!icon/action_left|!code/action_left"),
        BaseKey("!icon/action_right|!code/action_right"),
    ),
    attributes = KeyAttributes(
        width = KeyWidth.Grow,
        style = KeyVisualStyle.NoBackground,
        showPopup = false,
        repeatableEnabled = true
    ),
    splittable = false,
    rowHeight = 0.8
)

data class LayoutParams(
    val size: ComputedKeyboardSize,
    val gap: Dp,
    val standardRowHeight: Double,
    val element: KeyboardLayoutElement,
)

data class LayoutEngine(
    val context: Context,
    val keyboard: Keyboard,
    val params: KeyboardParams,
    val layoutParams: LayoutParams
) {
    val horizontalGap = layoutParams.gap
    val verticalGap = layoutParams.gap * 2

    val effectiveRows = keyboard.getEffectiveRows(params.mId.mNumberRowMode)

    private val rows = run {
        val filteredRows = effectiveRows.filter {
            // Filter the Number row, when it's not active
            when(keyboard.numberRowMode) {
                NumberRowMode.UserConfigurable -> !it.isNumberRow || params.mId.mNumberRow
                NumberRowMode.AlwaysEnabled    -> !it.isNumberRow || it.isNumberRow
                NumberRowMode.AlwaysDisabled   -> !it.isNumberRow
            }
        }.filter {
            // Display filler rows only when number row is explicitly active
            it.numRowMode.displayByDefault ||
                    (it.numRowMode.displayWhenExplicitlyActive && params.mId.mNumberRow) ||
                    (it.numRowMode.displayWhenExplicitlyInactive && !params.mId.mNumberRow)
        }

        val finalRows = filteredRows +
                if(params.mId.mArrowRow) { listOf(ArrowRow) } else { emptyList() }

        finalRows
    }

    private val density = context.resources.displayMetrics.density


    private val totalRowHeight = params.mId.mHeight.toFloat()

    private val horizontalGapPx = (horizontalGap.value * density)
    private val verticalGapPx = (verticalGap.value * density)
    private val rowHeightPx = computeRowHeight()
    private val bottomRowHeightPx = when(keyboard.bottomRowHeightMode) {
        BottomRowHeightMode.Fixed -> layoutParams.standardRowHeight
        BottomRowHeightMode.Flexible -> rowHeightPx
    }

    private fun computeRowHeight(): Double {
        val normalKeyboardHeight = totalRowHeight

        // divide by total row height
        return when(keyboard.bottomRowHeightMode) {
            BottomRowHeightMode.Fixed -> ((normalKeyboardHeight - layoutParams.standardRowHeight) / rows.filter { !it.isBottomRow }.sumOf { it.rowHeight })
            BottomRowHeightMode.Flexible -> (normalKeyboardHeight) / rows.sumOf { it.rowHeight }
        }
    }

    private val isSplitLayout = layoutParams.size is SplitKeyboardSize
    private val isOneHandedLayout = layoutParams.size is OneHandedKeyboardSize

    private val layoutWidth = if(isSplitLayout) {
        (layoutParams.size as SplitKeyboardSize).splitLayoutWidth
    } else if(isOneHandedLayout) {
        (layoutParams.size as OneHandedKeyboardSize).layoutWidth
    } else {
        params.mId.mWidth
    }

    private val unsplitLayoutWidth = if(isSplitLayout) {
        params.mId.mWidth
    } else {
        layoutWidth
    }

    private val minimumBottomFunctionalKeyWidth = (layoutWidth * keyboard.minimumBottomRowFunctionalKeyWidth)

    private val regularKeyWidth = computeRegularKeyWidth()
    private val unsplitRegularKeyWidth = computeRegularKeyWidth(unsplitLayoutWidth)
    private val minimumKeyWidth = regularKeyWidth / 2

    private val minFunctionalKeyWidth = layoutWidth * keyboard.minimumFunctionalKeyWidth
    private val maxFunctionalKeyWidth = (layoutWidth * maxOf(0.15f,
        keyboard.overrideWidths[KeyWidth.FunctionalKey] ?: 0.15f))

    private val bottomRegularKeyWidth = 0.1f * layoutWidth


    private fun computeRegularKeyWidth(layoutWidth: Int = this.layoutWidth): Float {
        return keyboard.overrideWidths[KeyWidth.Regular]?.let { it * layoutWidth.toFloat() } ?: run {
            (layoutWidth.toFloat() / effectiveRows.filter { it.isLetterRow }.maxOf { it.keys.size }.toFloat()) - EPS
        }
    }

    private fun computeRowWidths(row: List<LayoutEntry.Key>, regularKeyWidth: Float, layoutWidth: Float, functionalWidth: Float? = null): Map<KeyWidth, Float> {
        var availableSpace = layoutWidth

        val counts = mutableMapOf<KeyWidth, Int>().apply {
            KeyWidth.entries.forEach {
                set(it, row.count { key -> key.data.width == it })
            }
        }

        val rowWidths = keyboard.overrideWidths.mapValues {
            it.value * layoutWidth
        }.toMutableMap()


        // Special case: shrink regular key width for this row, if needed to fit functional keys
        val localRegularKeyWidth = run {
            if(functionalWidth != null) {
                val remainingSpace = availableSpace - counts.entries.sumOf {
                    if(it.value == 0) {
                        0.0
                    } else when(it.key) {
                        KeyWidth.Regular -> 0.0
                        KeyWidth.FunctionalKey -> functionalWidth.toDouble() * it.value
                        KeyWidth.Grow -> Double.NaN // TODO: Not sure what to do if a Grow is in the same row
                        else -> (rowWidths[it.key]?.toDouble() ?: 0.0) * it.value
                    }
                }.toFloat()

                val maxRegularKeyWidth = if(remainingSpace.isNaN()) {
                    Float.POSITIVE_INFINITY
                } else {
                    remainingSpace / counts[KeyWidth.Regular]!!.toFloat()
                }

                regularKeyWidth.coerceAtMost(maxRegularKeyWidth)
            } else {
                regularKeyWidth
            }
        }

        // Subtract regular keys
        rowWidths.putIfAbsent(KeyWidth.Regular, localRegularKeyWidth)
        availableSpace -= rowWidths[KeyWidth.Regular]!! * counts[KeyWidth.Regular]!!
        assert(availableSpace >= 0) {
            "Ran out of space!\nWidths: $rowWidths\nLayout width: $layoutWidth\nAvailable space: $availableSpace\nCounts: $counts"
        }
        counts.remove(KeyWidth.Regular)

        // Calculate functional key width, or use the provided one
        rowWidths.putIfAbsent(KeyWidth.FunctionalKey, functionalWidth ?:
            if(counts[KeyWidth.FunctionalKey]!! > 0) {
                if(counts[KeyWidth.Grow]!! > 0) {
                    // The width of functional keys is indeterminate within this row,
                    // we will need to do a second pass to find a fitting width.
                    // For now, set it to minimum width.
                    minimumKeyWidth
                } else {
                    // The width of functional keys fills the available space.
                    availableSpace / counts[KeyWidth.FunctionalKey]!!.toFloat()
                }
            } else {
                0.0f
            }
        )

        availableSpace -= rowWidths[KeyWidth.FunctionalKey]!! * counts[KeyWidth.FunctionalKey]!!.toFloat()
        //assert(availableSpace >= 0)
        counts.remove(KeyWidth.FunctionalKey)

        // Subtract remaining custom keys
        counts.forEach {
            if(it.key != KeyWidth.Grow && it.value > 0) {
                availableSpace -= it.value * (rowWidths[it.key] ?: throw IllegalStateException("Custom width ${it.key} is present, but width undeclared. Declared widths: $rowWidths"))
            }
        }

        // Set grow key width
        rowWidths.putIfAbsent(KeyWidth.Grow, if(counts[KeyWidth.Grow]!! > 0) {
            availableSpace / counts[KeyWidth.Grow]!!.toFloat()
        } else {
            0.0f
        })
        //assert(availableSpace >= 0.0f)

        return rowWidths
    }

    private fun alignForSplitLayout(rows: List<LayoutRow>): List<LayoutRow> {
        if(!isSplitLayout) return rows

        val splitWidths = rows.map { row ->
            if(!row.splittable) {
                0.0f
            } else if(row.entries.firstOrNull { it is LayoutEntry.Key && it.data.width == KeyWidth.Grow } != null) {
                0.0f
            } else {
                row.entries.splitRow().first.sumOf { it.widthPx.toDouble() }.toFloat()
            }
        }

        val maxSplitWidth = splitWidths.max()

        return rows.mapIndexed { i, row ->
            if(!row.splittable) return@mapIndexed row

            val currentRowWidth = splitWidths[i].let { width ->
                if(width == 0.0f) {
                    row.entries.sumOf {
                        if(it is LayoutEntry.Key && it.data.width == KeyWidth.Grow) {
                            0.0f
                        } else {
                            it.widthPx
                        }.toDouble()
                    }.toFloat()
                } else {
                    width
                }
            }

            val extraSpace = maxSplitWidth - currentRowWidth

            val growTarget = if(splitWidths[i] == 0.0f) {
                KeyWidth.Grow
            } else {
                KeyWidth.Regular
            }

            val growableKeyCount = row.entries.filterIsInstance<LayoutEntry.Key>().count {
                it.data.width == growTarget
            }

            val widthPerKey = 2 * extraSpace / growableKeyCount
            LayoutRow(
                entries = row.entries.map { entry ->
                    if(entry is LayoutEntry.Key && entry.data.width == growTarget) {
                        LayoutEntry.Key(entry.data, if(growTarget == KeyWidth.Regular) {
                            entry.widthPx + widthPerKey
                        } else {
                            widthPerKey
                        })
                    } else {
                        entry
                    }
                },
                widths = row.widths,
                height = row.height,
                splittable = row.splittable
            )
        }
    }

    private fun buildLayoutRow(
        computedRowWithoutWidths: List<LayoutEntry.Key>,
        widths: Map<KeyWidth, Float>,
        height: Float,
        splittable: Boolean
    ): LayoutRow {
        val computedRow = computedRowWithoutWidths.map { key ->
            LayoutEntry.Key(key.data, widths[key.data.width]!!)
        }

        val totalRowWidth = computedRow.sumOf { it.widthPx.toDouble() }.toFloat()

        val rowLayoutWidth = if(splittable) { layoutWidth } else { unsplitLayoutWidth }
        val entries = mergeDuplicates(computedRow.addGap(rowLayoutWidth - totalRowWidth))

        return LayoutRow(
            entries = entries,
            widths = widths,
            height = height,
            splittable = splittable
        )
    }

    private fun computeRows(rows: List<Row>): List<LayoutRow> {
        val rowLayoutWidths = rows.map {
            if(it.splittable) {
                layoutWidth
            } else {
                unsplitLayoutWidth
            }.toFloat()
        }

        val rowRegularKeyWidths = rows.map {
            // Special case: action row uses regular key width to match the other rows
            if(it.isBottomRow) {
                bottomRegularKeyWidth
            } else if(it.splittable) {
                regularKeyWidth
            } else {
                unsplitRegularKeyWidth
            }.toFloat()
        }

        // Measure key coordinate
        val numColumnsPerRow = mutableListOf<Int>()
        rows.forEach { row ->
            val numColumns = row.keys.sumOf { if(it.countsToKeyCoordinate(params, row, keyboard)) (1 as Int) else 0 }
            if(numColumns > 0) {
                numColumnsPerRow.add(numColumns)
            }
        }

        val keyCoordinateMeasurement = KeyCoordinateMeasurement(
            totalRows = numColumnsPerRow.size,
            numColumnsByRow = numColumnsPerRow.toList()
        )

        var regularRow = 0
        val computedRowWithoutWidths = rows.map { row ->
            var regularColumn = 0
            row.keys.mapNotNull { key ->
                val coordinate = KeyCoordinate(
                    regularRow,
                    regularColumn,
                    layoutParams.element,
                    keyCoordinateMeasurement
                )

                key.computeData(params, row, keyboard, coordinate)?.let { data ->
                    if(data.countsToKeyCoordinate) {
                        regularColumn += 1
                    }

                    LayoutEntry.Key(data, widthPx = -1.0f)
                }
            }.let {
                if (regularColumn > 0) {
                    regularRow += 1
                }
                it
            }
        }

        val rowWidths = computedRowWithoutWidths.mapIndexed { i, it ->
            computeRowWidths(
                row = it,
                regularKeyWidth = rowRegularKeyWidths[i],
                layoutWidth = rowLayoutWidths[i]
            )
        }.let { preWidths ->
            // Find the smallest functional key width
            val functionalWidth = (
                preWidths.map { it[KeyWidth.FunctionalKey]!! }
                    .filter { it > minimumKeyWidth }
                    .minOrNull() ?: minimumKeyWidth
            ).coerceIn(minFunctionalKeyWidth, maxFunctionalKeyWidth)

            computedRowWithoutWidths.mapIndexed { i, it ->
                computeRowWidths(
                    row = it,
                    regularKeyWidth = rowRegularKeyWidths[i],
                    layoutWidth = rowLayoutWidths[i],
                    functionalWidth = if (rows[i].isBottomRow && keyboard.bottomRowWidthMode.separateFunctional) {
                        maxOf(minimumBottomFunctionalKeyWidth, functionalWidth)
                    } else {
                        functionalWidth
                    }
                )
            }
        }


        val computedRowWithWidths = computedRowWithoutWidths.mapIndexed { i, row ->
            val height = if(rows[i].isBottomRow) {
                bottomRowHeightPx
            } else {
                (rows[i].rowHeight * rowHeightPx)
            }

            buildLayoutRow(
                row,
                rowWidths[i],
                height.toFloat(),
                rows[i].splittable
            )
        }

        return alignForSplitLayout(computedRowWithWidths)
    }

    private fun mergeDuplicates(row: List<LayoutEntry>): List<LayoutEntry> {
        if(row.isEmpty()) return emptyList()

        return row.fold(mutableListOf()) { acc, curr ->
            if(acc.isEmpty()) {
                acc.add(curr)
            } else {
                val last = acc.last()
                if (last is LayoutEntry.Key && curr is LayoutEntry.Key && last.data == curr.data) {
                    val lastVal = acc.removeAt(acc.size - 1)
                    acc.add(LayoutEntry.Key(
                        data = curr.data,
                        widthPx = last.widthPx + curr.widthPx
                    ))
                } else {
                    acc.add(curr)
                }
            }

            acc
        }
    }

    private val validNumbersForHint = "1234567890".map { it.toString() }.map { params.mTextsSet.resolveTextReference("!text/keyspec_symbols_$it") ?: it }.toSet()
    private val englishNumbers = "1234567890".map { it.toString() }.toSet()
    private val blacklistedHintsForNumbers = buildList {
        // Subscripts/superscripts, fractions
        addAll((0x2070 .. 0x208E).map { it.toChar() })
        addAll((0x2150 .. 0x215F).map { it.toChar() })
        add('\u00B0')
        add('\u00B2')
        add('\u00B3')
        add('\u00B9')
        add('\u00BC')
        add('\u00BD')
        add('\u00BE')
    }.map { it.toString() }.toSet()
    private val showAllHintsSetting = params.mId.mLongPressKeySettings.showHints

    private fun computedKeyToKey(row: Int, col: Int, data: ComputedKeyData, x: Int, y: Int, width: Int, height: Int, leftGap: LayoutEntry.Gap?, rightGap: LayoutEntry.Gap?): org.futo.inputmethod.keyboard.Key? {
        // These keys are empty keys and do not get added, leaving an empty gap in place of the key
        // The hitbox of other keys does not get expanded to include this gap though, unlike
        // gaps added for centering rows
        if(data.label.isEmpty() && data.icon.isEmpty() && data.code == Constants.CODE_UNSPECIFIED)
            return null


        val actionsFlags = if(!data.showPopup) { KeyConsts.ACTION_FLAGS_NO_KEY_PREVIEW } else { 0 } or
                if(data.longPressEnabled) { KeyConsts.ACTION_FLAGS_ENABLE_LONG_PRESS } else { 0 } or
                if(data.repeatable) { KeyConsts.ACTION_FLAGS_IS_REPEATABLE } else { 0 }

        val verticalGapForKey = when {
            keyboard.rowHeightMode.clampHeight && height > layoutParams.standardRowHeight ->
                height - layoutParams.standardRowHeight

            else ->
                0.0
        } + verticalGapPx

        val hasExplicitHint = data.hint.isNotEmpty()

        val hint = when {
            // If we have an explicit hint, use the specified hint
            hasExplicitHint -> data.hint

            // If we have no explicit hint, and show hints setting is disabled,
            // then either show a number or nothing
            !showAllHintsSetting -> //&& params.mId.mElement.kind.isAlphabet ->
                data.moreKeys.mapNotNull { it.mLabel }.firstOrNull { validNumbersForHint.contains(it) && it != data.label } ?: ""

            // In the situation that this is a number, only show a hint if it's not a tiny little
            // superscript or subscript
            englishNumbers.contains(data.label) && validNumbersForHint.contains(data.label)
                ->
                data.moreKeys.firstOrNull {
                    it.mLabel != null
                            && !blacklistedHintsForNumbers.contains(it.mLabel)
                            && it.mLabel != data.label
                }?.mLabel ?: ""

            // If we have no explicit hint and show hints setting is enabled (or we are in symbols menu),
            // set this to null which will later automatically select a hint based on
            // the first element of moreKeys
            else -> null
        }

        // For the spacebar specifically, extend the hitbox all the way down to include the padded area,
        // unless arrow row is active
        val extendedBottomPadding = if(data.style == KeyVisualStyle.Spacebar && data.code == Constants.CODE_SPACE && !params.mId.mArrowRow) {
            layoutParams.size.padding.bottom
        } else {
            0
        }

        return org.futo.inputmethod.keyboard.Key(
            code = data.code,
            label = data.label,
            width = width - horizontalGapPx.roundToInt(),
            height = height - verticalGapForKey.roundToInt(),
            iconId = data.icon,
            x = x,
            y = y,
            actionFlags = actionsFlags,
            horizontalGap = horizontalGapPx.roundToInt(),
            verticalGap = verticalGapForKey.roundToInt(),
            labelFlags = data.labelFlags,
            moreKeys = data.moreKeys,
            moreKeysColumnAndFlags = data.moreKeyFlags,
            visualStyle = data.style,
            outputText = data.outputText,
            hintLabel = hint,
            isFastLongPress = data.fastLongPress,

            // Add leftGap and rightGap to the hitbox
            // This makes the following area tappable,
            // otherwise taps there wouldn't be registered
            // q w e r t y u i o p
            // #a s d f g h j k l#
            // ^                 ^
            // taps A       taps L
            // Also add bottom padding for spacebar
            hitBox = Rect(
                x - (leftGap?.widthPx?.roundToInt() ?: 0),
                y,
                x + width + (rightGap?.widthPx?.roundToInt() ?: 0),
                y + height + extendedBottomPadding
            ),

            // Add flick keys
            flickKeys = data.flick?.let {
                it.directions
                    .mapValues { computedKeyToKey(0, 0, it.value, x, y, width, height, null, null) }
                    .filterValues { it != null }
                    .mapValues { it.value!! }
            },

            labelOverride = data.flick?.label,
            iconOverride = data.flick?.icon,

            row = row,
            column = col
        )
    }

    private fun addKey(row: Int, col: Int, data: ComputedKeyData, x: Int, y: Int, width: Int, height: Int, leftGap: LayoutEntry.Gap?, rightGap: LayoutEntry.Gap?) {
        val key = computedKeyToKey(row, col, data, x, y, width, height, leftGap, rightGap) ?: return
        params.onAddKey(key)
    }

    private fun addRow(idx: Int, row: List<LayoutEntry>, x: Float, y: Int, height: Int) {
        var currentX = x
        var colNum = 0
        row.forEachIndexed { i, entry ->
            when(entry) {
                is LayoutEntry.Gap -> { }
                is LayoutEntry.Key -> {
                    // Adding gaps is only applicable on unanchored keys on the correct side of the keyboard
                    val leftGap = if(i < row.size / 2 && !entry.data.anchored) { row.getOrNull(i - 1) as? LayoutEntry.Gap } else { null }
                    val rightGap = if(i >= row.size / 2 && !entry.data.anchored) { row.getOrNull(i + 1) as? LayoutEntry.Gap } else { null }

                    addKey(idx, colNum, entry.data, currentX.roundToInt(), y, entry.widthPx.roundToInt(), height, leftGap, rightGap)

                    // i != colNum because of Gap entries
                    colNum += 1
                }
            }

            currentX += entry.widthPx
        }
    }

    private fun addRowAlignLeft(idx: Int, row: List<LayoutEntry>, y: Int, height: Int)
            = addRow(idx, row, 0.0f, y, height)

    private fun addRowAlignRight(idx: Int, row: List<LayoutEntry>, y: Int, height: Int) {
        val startingOffset = params.mId.mWidth - row.sumOf { it.widthPx.toDouble() }.toFloat()
        addRow(idx, row, startingOffset, y, height)
    }

    private fun addRow(idx: Int, row: LayoutRow, y: Int) {
        if(isSplitLayout && row.splittable) {
            val splitRows = row.entries.splitRow()

            addRowAlignLeft(idx, splitRows.first,   y, row.height.toInt())
            addRowAlignRight(idx, splitRows.second, y, row.height.toInt())
        } else {
            addRowAlignLeft(idx, row.entries, y, row.height.toInt())
        }
    }

    private fun addKeys(rows: List<LayoutRow>): Int {
        var currentY = 0.0f
        rows.forEachIndexed { i, row ->
            addRow(i, row, currentY.toInt())
            currentY += row.height
        }

        return currentY.roundToInt()
    }

    fun build(): org.futo.inputmethod.keyboard.Keyboard {
        params.mMoreKeysTemplate = R.xml.kbd_more_keys_keyboard_template
        params.mMaxMoreKeysKeyboardColumn = 5

        params.GRID_WIDTH = context.resources.getInteger(R.integer.config_keyboard_grid_width)
        params.GRID_HEIGHT = context.resources.getInteger(R.integer.config_keyboard_grid_height)

        val rows = computeRows(this.rows)

        val totalKeyboardHeight = addKeys(rows).let { totalRowHeight.roundToInt() } + layoutParams.size.padding.bottom

        params.mOccupiedHeight = totalKeyboardHeight - verticalGapPx.roundToInt()
        params.mOccupiedWidth = params.mId.mWidth
        params.mTopPadding    = 0
        params.mBottomPadding = layoutParams.size.padding.bottom
        params.mLeftPadding   = 0
        params.mRightPadding  = 0

        params.mBaseWidth = params.mOccupiedWidth
        params.mDefaultKeyWidth = regularKeyWidth.roundToInt()
        params.mHorizontalGap = 0
        params.mVerticalGap = 0
        params.mBaseHeight = totalKeyboardHeight
        params.mDefaultRowHeight = rowHeightPx.roundToInt()
        params.mAutoShift = keyboard.autoShift

        try {
            val provider = DynamicThemeProvider.obtainFromContext(context)
            params.mIconsSet.loadIcons(null, provider)
        } catch(_: IllegalArgumentException) {
            Log.w("LayoutEngine", "Unable to load icons, this may cause an exception if it's used later")
        }

        params.mThemeId = 3
        params.mTextsSet.setLocale(params.mId.locale, context)

        params.mProximityCharsCorrectionEnabled = true

        params.mAllowRedundantMoreKeys = true
        params.removeRedundantMoreKeys()

        params.mMostCommonKeyWidth = regularKeyWidth.roundToInt()

        return org.futo.inputmethod.keyboard.Keyboard(params)
    }
}