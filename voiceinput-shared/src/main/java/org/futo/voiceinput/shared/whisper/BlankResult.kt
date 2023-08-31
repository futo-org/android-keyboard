package org.futo.voiceinput.shared.whisper

private fun createBlankResultPermutations(blankResults: List<String>): HashSet<String> {
    val blankResultsResult = blankResults.map { it.lowercase() }.toMutableList()

    blankResultsResult += blankResultsResult.map {
        it.replace("(", "[").replace(")", "]")
    }
    blankResultsResult += blankResultsResult.map {
        it.replace(" ", "_")
    }

    return blankResultsResult.map { it.lowercase() }.toHashSet()
}

private val EMPTY_RESULTS = createBlankResultPermutations(
    listOf(
        "you", "(bell dings)", "(blank audio)", "(beep)", "(bell)", "(music)", "(music playing)"
    )
)

fun isBlankResult(result: String): Boolean {
    return EMPTY_RESULTS.contains(result)
}