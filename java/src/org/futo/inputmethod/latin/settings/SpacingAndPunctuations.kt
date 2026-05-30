/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.futo.inputmethod.latin.settings

import android.content.Context
import android.util.Xml
import androidx.collection.LruCache
import org.futo.inputmethod.annotations.UsedForTesting
import org.futo.inputmethod.keyboard.internal.MoreKeySpec
import org.futo.inputmethod.latin.PunctuationSuggestions
import org.futo.inputmethod.latin.common.StringUtils
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.InputStream
import java.util.Arrays
import java.util.Locale


internal fun String?.toSortedCodePointArray() = this?.let { StringUtils.toSortedCodePointArray(it) } ?: intArrayOf()

internal data class ParsedData(
    val strings: Map<String, String>,
    val ints: Map<String, Int>,
    val bools: Map<String, Boolean>
) {
    operator fun plus(other: ParsedData): ParsedData =
        ParsedData(
            // The 2nd element in Map.plus has precedence over the first
            // but in our case, we want (fr-rCA) + (fr) + (default) to be meaningful
            strings = other.strings + strings,
            ints = other.ints + ints,
            bools = other.bools + bools
        )

    companion object {
        private const val ASSET_PATH = "spacing-and-punctuations"

        private val cache = LruCache<String, ParsedData>(10)

        fun load(context: Context, locale: Locale, isSw600dp: Boolean): ParsedData {
            val candidates = buildCandidateList(locale, isSw600dp)

            val topLevelCandidate = candidates[0]

            return cache[topLevelCandidate] ?: run {
                val parsed = candidates.mapNotNull { candidate ->
                    try {
                        val path = "$ASSET_PATH/$candidate.xml"
                        context.assets.open(path).use { stream ->
                            parseXml(stream)
                        }
                    } catch (e: IOException) {
                        null
                    }
                }

                if(parsed.isEmpty()) throw IllegalStateException("No spacing and punctuation found at all.")

                val combined = parsed.reduce { a, b -> a + b }

                cache.put(topLevelCandidate, combined)

                combined
            }
        }

        private fun parseXml(stream: InputStream): ParsedData {
            val parser = Xml.newPullParser()
            parser.setInput(stream, "UTF-8")

            val strings = mutableMapOf<String, String>()
            val ints = mutableMapOf<String, Int>()
            val bools = mutableMapOf<String, Boolean>()

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    val name = parser.getAttributeValue(null, "name")
                    when (parser.name) {
                        "string" -> {
                            parser.next()
                            if (parser.eventType == XmlPullParser.TEXT) {
                                strings[name] = parser.text
                            } else if (parser.eventType == XmlPullParser.END_TAG) {
                                strings[name] = ""
                            }
                        }
                        "integer" -> {
                            parser.next()
                            if (parser.eventType == XmlPullParser.TEXT) {
                                ints[name] = parser.text.toIntOrNull() ?: 0
                            }
                        }
                        "bool" -> {
                            parser.next()
                            if (parser.eventType == XmlPullParser.TEXT) {
                                bools[name] = parser.text.toBoolean()
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            return ParsedData(strings, ints, bools)
        }

        fun buildCandidateList(locale: Locale, isSw600dp: Boolean): List<String> {
            val candidates = mutableListOf<String>()
            val language = locale.language
            val region = locale.country.takeIf { it.isNotEmpty() }

            // Full tag with region (e.g., "fr-rCA")
            val fullTag = if (region != null) "${language}-r${region}" else language

            // 1. With sw600dp qualifier (most specific)
            if (isSw600dp) {
                candidates.add("$fullTag-sw600dp")
                if (region != null) {
                    candidates.add("${language}-sw600dp")
                }
            }

            // 2. Without sw600dp
            candidates.add(fullTag)
            if (region != null) {
                candidates.add(language)
            }

            // 3. Fallbacks
            if (isSw600dp) {
                candidates.add("default-sw600dp")
            }
            candidates.add("default")

            return candidates.distinct()
        }
    }
}


class SpacingAndPunctuations(
    @JvmField
    val sortedWordSeparators: IntArray,
    @JvmField
    val suggestPuncList: PunctuationSuggestions,
    @JvmField
    val sentenceSeparatorAndSpace: String,
    @JvmField
    val currentLanguageHasSpaces: Boolean,
    @JvmField
    val usesAmericanTypography: Boolean,
    @JvmField
    val usesGermanRules: Boolean,
    private val sortedSymbolsPrecededBySpace: IntArray,
    private val sortedSymbolsOptionallyPrecededBySpace: IntArray,
    private val sortedSymbolsFollowedBySpace: IntArray,
    private val sortedSymbolsFollowedBySpaceIffPrecededBySpace: IntArray,
    private val sortedSymbolsClusteringTogether: IntArray,
    private val sortedWordConnectors: IntArray,
    private val sortedSentenceTerminators: IntArray,
    private val sentenceSeparator: Int,
    private val abbreviationMarker: Int
) {
    companion object {
        @JvmStatic
        fun create(context: Context, locale: Locale = context.resources.configuration.locale): SpacingAndPunctuations {
            val isSw600dp = context.resources.configuration.smallestScreenWidthDp >= 600
            val config = ParsedData.load(context, locale, isSw600dp)
            return fromConfig(locale, config)
        }

        @JvmStatic
        @UsedForTesting
        fun createForTesting(
            model: SpacingAndPunctuations,
            overrideSortedWordSeparators: IntArray
        ): SpacingAndPunctuations {
            return SpacingAndPunctuations(
                sortedWordSeparators = overrideSortedWordSeparators,
                suggestPuncList = model.suggestPuncList,
                sentenceSeparatorAndSpace = model.sentenceSeparatorAndSpace,
                currentLanguageHasSpaces = model.currentLanguageHasSpaces,
                usesAmericanTypography = model.usesAmericanTypography,
                usesGermanRules = model.usesGermanRules,
                sortedSymbolsPrecededBySpace = model.sortedSymbolsPrecededBySpace,
                sortedSymbolsOptionallyPrecededBySpace = model.sortedSymbolsOptionallyPrecededBySpace,
                sortedSymbolsFollowedBySpace = model.sortedSymbolsFollowedBySpace,
                sortedSymbolsFollowedBySpaceIffPrecededBySpace = model.sortedSymbolsFollowedBySpaceIffPrecededBySpace,
                sortedSymbolsClusteringTogether = model.sortedSymbolsClusteringTogether,
                sortedWordConnectors = model.sortedWordConnectors,
                sortedSentenceTerminators = model.sortedSentenceTerminators,
                sentenceSeparator = model.sentenceSeparator,
                abbreviationMarker = model.abbreviationMarker
            )
        }

        internal fun fromConfig(locale: Locale, config: ParsedData): SpacingAndPunctuations {
            val sentenceSep = config.ints["sentence_separator"]!! // '.' default
            val abbrevMarker = config.ints["abbreviation_marker"]!!

            return SpacingAndPunctuations(
                sortedSymbolsPrecededBySpace = config.strings["symbols_preceded_by_space"].toSortedCodePointArray(),
                sortedSymbolsFollowedBySpace = config.strings["symbols_followed_by_space"].toSortedCodePointArray(),
                sortedSymbolsOptionallyPrecededBySpace = config.strings["symbols_optionally_preceded_by_space"].toSortedCodePointArray(),
                sortedSymbolsFollowedBySpaceIffPrecededBySpace = config.strings["symbols_followed_by_space_iff_preceded_by_space"].toSortedCodePointArray(),
                sortedSymbolsClusteringTogether = config.strings["symbols_clustering_together"].toSortedCodePointArray(),
                sortedWordConnectors = config.strings["symbols_word_connectors"].toSortedCodePointArray(),
                sortedWordSeparators = config.strings["symbols_word_separators"].toSortedCodePointArray(),
                sortedSentenceTerminators = config.strings["symbols_sentence_terminators"].toSortedCodePointArray(),
                sentenceSeparator = sentenceSep,
                abbreviationMarker = abbrevMarker,
                sentenceSeparatorAndSpace = String(intArrayOf(sentenceSep, ' '.code), 0, 2),
                currentLanguageHasSpaces = config.bools["current_language_has_spaces"]!!,

                // Heuristic: we use American Typography rules because it's the most common rules for all
                // English variants. German rules (not "German typography") also have small gotchas.
                usesAmericanTypography = locale.language == Locale.ENGLISH.language,
                usesGermanRules = locale.language == Locale.GERMAN.language,

                // Unused.
                suggestPuncList = PunctuationSuggestions.newPunctuationSuggestions(
                    MoreKeySpec.splitKeySpecs(config.strings["suggested_punctuations"]!!)
                )
            )
        }
    }


    fun isWordSeparator(code: Int): Boolean {
        return Arrays.binarySearch(sortedWordSeparators, code) >= 0
    }

    fun isWordConnector(code: Int): Boolean {
        return Arrays.binarySearch(sortedWordConnectors, code) >= 0
    }

    fun isWordCodePoint(code: Int): Boolean {
        return Character.isLetter(code) || isWordConnector(code)
    }

    fun isUsuallyPrecededBySpace(code: Int): Boolean {
        return Arrays.binarySearch(sortedSymbolsPrecededBySpace, code) >= 0
    }

    fun isOptionallyPrecededBySpace(code: Int): Boolean {
        return Arrays.binarySearch(sortedSymbolsOptionallyPrecededBySpace, code) >= 0
    }

    fun isUsuallyFollowedBySpace(code: Int): Boolean {
        return Arrays.binarySearch(sortedSymbolsFollowedBySpace, code) >= 0
    }

    fun isUsuallyFollowedBySpaceIffPrecededBySpace(code: Int): Boolean {
        return Arrays.binarySearch(sortedSymbolsFollowedBySpaceIffPrecededBySpace, code) >= 0
    }

    fun isClusteringSymbol(code: Int): Boolean {
        return Arrays.binarySearch(sortedSymbolsClusteringTogether, code) >= 0
    }

    fun isSentenceTerminator(code: Int): Boolean {
        return Arrays.binarySearch(sortedSentenceTerminators, code) >= 0
    }

    fun isAbbreviationMarker(code: Int): Boolean {
        return code == abbreviationMarker
    }

    fun isSentenceSeparator(code: Int): Boolean {
        return code == sentenceSeparator
    }

    fun dump(): String = buildString {
        append("   sortedSymbolsPrecededBySpace = ${sortedSymbolsPrecededBySpace.contentToString()}\n")
        append("   sortedSymbolsFollowedBySpace = ${sortedSymbolsFollowedBySpace.contentToString()}\n")
        append("   sortedSymbolsOptionallyPrecededBySpace = ${sortedSymbolsOptionallyPrecededBySpace.contentToString()}\n")
        append("   sortedSymbolsFollowedBySpaceIffPrecededBySpace = ${sortedSymbolsFollowedBySpaceIffPrecededBySpace.contentToString()}\n")
        append("   sortedSymbolsClusteringTogether = ${sortedSymbolsClusteringTogether.contentToString()}\n")
        append("   sortedWordConnectors = ${sortedWordConnectors.contentToString()}\n")
        append("   sortedWordSeparators = ${sortedWordSeparators.contentToString()}\n")
        append("   suggestPuncList = $suggestPuncList\n")
        append("   sentenceSeparator = $sentenceSeparator\n")
        append("   sentenceSeparatorAndSpace = $sentenceSeparatorAndSpace\n")
        append("   currentLanguageHasSpaces = $currentLanguageHasSpaces\n")
        append("   usesAmericanTypography = $usesAmericanTypography\n")
        append("   usesGermanRules = $usesGermanRules")
    }
}
