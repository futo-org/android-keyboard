package org.futo.voiceinput.shared.whisper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.futo.voiceinput.shared.ggml.BailLanguageException
import org.futo.voiceinput.shared.ggml.DecodingMode
import org.futo.voiceinput.shared.ggml.InferenceCancelledException
import org.futo.voiceinput.shared.types.InferenceState
import org.futo.voiceinput.shared.types.Language
import org.futo.voiceinput.shared.types.ModelInferenceCallback
import org.futo.voiceinput.shared.types.ModelLoader
import org.futo.voiceinput.shared.types.getLanguageFromWhisperString
import org.futo.voiceinput.shared.types.toWhisperString
import java.util.Locale


data class MultiModelRunConfiguration(
    val primaryModel: ModelLoader,
    val languageSpecificModels: Map<Language, ModelLoader>
)

data class TextContext(
    val beforeCursor: CharSequence?,
    val afterCursor: CharSequence?
)

data class DecodingConfiguration(
    val glossary: List<String>,
    val languages: Set<Language>,
    val suppressSymbols: Boolean,
    val textContext: TextContext?
)

class MultiModelRunner(
    private val modelManager: ModelManager
) {
    private fun Char.isPunctuation(): Boolean {
        return this in setOf('.', ',', '!', '?', ':', ';')
    }

    private fun Char.isClosingBracket(): Boolean {
        return this in setOf(')', ']', '}', '>')
    }

    private fun Char.isOpeningBracket(): Boolean {
        return this in setOf('(', '[', '{', '<')
    }

    private fun String.endsWithWhitespaceOrNewline(): Boolean {
        return this.isNotEmpty() && this.last().isWhitespace()
    }

    private fun String.startsWithWhitespaceOrNewline(): Boolean {
        return this.isNotEmpty() && this.first().isWhitespace()
    }

    internal fun sanitizeResult(result: String, textContext: TextContext?): String {
        if (textContext == null) {
            return result
        }

        var trimmed = result.trim()
        if (trimmed.isEmpty()) {
            return ""
        }

        val before = (textContext.beforeCursor?.toString() ?: "")
            .split("\n")
            .lastOrNull() ?: ""
        val after = (textContext.afterCursor?.toString() ?: "")
            .split("\n")
            .firstOrNull() ?: ""

        // Whisper tends to generate ellipsis at the end of phrases/sentences more often than appropriate. Which isn't so bad if it's at the very end but frustrating when inserting something inside of a sentence.
        if (trimmed.endsWith("...") && !after.isEmpty()) {
            trimmed = trimmed.dropLast(3)
        }

        // Punctuation
        if (trimmed.last().isPunctuation() && !after.isEmpty()) {
            trimmed = trimmed.dropLast(1)
        }

        // Capitalization
        val beforeTrimmed = before.trimEnd()
        val needsCapitalization = beforeTrimmed.isEmpty() || beforeTrimmed.matches(Regex(".*[.:?!]$"))
        val isAcronym = trimmed.length >= 2 &&
            trimmed[0].isUpperCase() &&
            trimmed[1].isUpperCase()
        if (needsCapitalization && trimmed.first().isLowerCase()) {
            trimmed = trimmed.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        } else if (!beforeTrimmed.isEmpty() && trimmed.first().isUpperCase() && !isAcronym) {
            trimmed = trimmed.replaceFirstChar { it.lowercase(Locale.getDefault()) }
        }

        // Leading and trailing spaces
        val needsLeadingSpace = before.isNotEmpty() && !before.endsWithWhitespaceOrNewline() &&
            !before.last().isOpeningBracket()
        val needsTrailingSpace = after.isNotEmpty() &&
            !after.startsWithWhitespaceOrNewline() &&
            !after.first().isPunctuation() &&
            !after.first().isClosingBracket()

        val prefix = if (needsLeadingSpace) " " else ""
        val suffix = if (needsTrailingSpace) " " else ""

        return prefix + trimmed + suffix
    }

    suspend fun preload(runConfiguration: MultiModelRunConfiguration) = coroutineScope {
        val jobs = mutableListOf<Job>()

        jobs.add(launch(Dispatchers.Default) {
            modelManager.obtainModel(runConfiguration.primaryModel)
        })

        if (runConfiguration.languageSpecificModels.count() < 2) {
            runConfiguration.languageSpecificModels.forEach {
                jobs.add(launch(Dispatchers.Default) {
                    modelManager.obtainModel(it.value)
                })
            }
        }

        jobs.forEach { it.join() }
    }

    @Throws(InferenceCancelledException::class)
    suspend fun run(
        samples: FloatArray,
        runConfiguration: MultiModelRunConfiguration,
        decodingConfiguration: DecodingConfiguration,
        callback: ModelInferenceCallback
    ): String = coroutineScope {
        callback.updateStatus(InferenceState.LoadingModel)
        val primaryModel = modelManager.obtainModel(runConfiguration.primaryModel)

        val allowedLanguages = decodingConfiguration.languages.map { it.toWhisperString() }.toTypedArray()
        val bailLanguages = runConfiguration.languageSpecificModels.filter { it.value != runConfiguration.primaryModel }.keys.map { it.toWhisperString() }.toTypedArray()

        val glossary = if(decodingConfiguration.glossary.isNotEmpty()) {
            "(Glossary: " + decodingConfiguration.glossary.joinToString(separator = ", ") + ")"
        } else {
            ""
        }

        val result = try {
            callback.updateStatus(InferenceState.Encoding)
            primaryModel.infer(
                samples = samples,
                prompt = glossary,
                languages = allowedLanguages,
                bailLanguages = bailLanguages,
                decodingMode = DecodingMode.BeamSearch5,
                suppressNonSpeechTokens = true,
                partialResultCallback = {
                    callback.partialResult(it)
                }
            )
        } catch(e: BailLanguageException) {
            callback.updateStatus(InferenceState.SwitchingModel)
            val language = getLanguageFromWhisperString(e.language)

            val specificModelLoader = runConfiguration.languageSpecificModels[language]!!
            val specificModel = modelManager.obtainModel(specificModelLoader)

            specificModel.infer(
                samples = samples,
                prompt = glossary,
                languages = arrayOf(e.language),
                bailLanguages = arrayOf(),
                decodingMode = DecodingMode.BeamSearch5,
                suppressNonSpeechTokens = true,
                partialResultCallback = {
                    callback.partialResult(it)
                }
            )
        }

        return@coroutineScope sanitizeResult(result, decodingConfiguration.textContext)
    }

    fun cancelAll() {
        modelManager.cancelAll()
    }
}