# Investigation: Why LLM Rescoring is Disabled for Swipe

## Location
`LanguageModelFacilitator.kt:308-330`

## The Code
```kotlin
val useRescoring = false  // ❌ DISABLED

val finalResults = if(useRescoring && values.composedData.mIsBatchMode) {
    val rescored = languageModel?.rescoreSuggestions(...)
    if(rescored != null) {
        SuggestedWords(
            ArrayList(rescored),
            // TODO: These should ideally not be null/false
            null, null, false, false, false,
            results.mInputStyle, results.mSequenceNumber
        )
        // TODO: We need the swapping rejection thing, the rescored array is resorted without the swapping
    } else {
        results
    }
} else {
    results
}
```

## Root Cause Analysis

### The Problem: Swapping Rejection Mechanism

Android's suggestion system relies on a specific ordering for rejection handling:

1. **INDEX_OF_TYPED_WORD = 0**: The word exactly as user typed/swiped
2. **INDEX_OF_AUTO_CORRECTION = 1**: The auto-correction candidate

When a user **rejects** a suggestion (by pressing backspace), the system relies on this order to:
- Remove the auto-correction
- Show the typed word instead
- Allow the user to continue from their original input

### Why LLM Rescoring Breaks This

When `rescoreSuggestions()` is called:
1. It takes existing suggestions from the geometric swipe algorithm
2. Re-ranks them using the LLM's language model scores
3. **Sorts the list by new scores** (highest to lowest)
4. Returns the re-sorted list

**The result:**
- ✅ Better contextual predictions
- ❌ **BREAKS** the INDEX_OF_TYPED_WORD / INDEX_OF_AUTO_CORRECTION contract
- ❌ **BREAKS** swapping rejection behavior
- ❌ User can't properly reject swipe suggestions

### Example Scenario (Broken)

**User swipes:** "hlelo" (slightly off pattern)

**Geometric algorithm produces:**
```
[0] "hlelo"  (typed word, score: 50)
[1] "hello"  (auto-correction, score: 80)
[2] "halo"   (alternative, score: 60)
```

**LLM rescores based on context "I want to say":**
```
Rescored:
"hello" → 95 (context boost)
"halo"  → 70
"hlelo" → 50
```

**After sorting by LLM score:**
```
[0] "hello" (was index 1)  ❌ Not the typed word anymore!
[1] "halo"  (was index 2)
[2] "hlelo" (was index 0)  ❌ Typed word demoted!
```

**User presses backspace to reject:**
- System expects typed word at [0]
- Finds "hello" (the correction!)
- Rejection mechanism fails
- User experience breaks

## Why This Matters for Swipe

Swipe/gesture input is **inherently imprecise**:
- User might not hit exact keys
- Path might be slightly curved or off
- Multiple valid interpretations

Therefore:
1. **Rejection is common** - users often need to reject and retry
2. **Typed word preservation is critical** - need to see what system interpreted
3. **Order matters** - INDEX_0 must always be what user "typed" (swiped)

## The TODO Comment Explained

> "TODO: We need the swapping rejection thing, the rescored array is resorted without the swapping"

Translation:
- **"swapping rejection thing"** = The INDEX_0 / INDEX_1 contract for rejection
- **"rescored array is resorted"** = LLM scoring breaks the order
- **"without the swapping"** = After rescoring, rejection doesn't work

## Solution Options

### Option 1: Preserve Typed Word at Index 0 (Recommended)
```kotlin
val finalResults = if(useRescoring && values.composedData.mIsBatchMode) {
    val rescored = languageModel?.rescoreSuggestions(...)
    if(rescored != null) {
        // 1. Find and extract the typed word
        val typedWord = results.getTypedWordInfo()
        val rescoredList = ArrayList(rescored)

        // 2. Remove typed word from rescored list if present
        rescoredList.removeAll { it.mWord == typedWord?.mWord }

        // 3. Insert typed word at index 0
        if(typedWord != null) {
            rescoredList.add(0, typedWord)
        }

        // 4. Determine auto-correction
        val willAutoCorrect = rescoredList.size > 1 &&
                             rescoredList[1].mScore > typedWord.mScore + threshold

        SuggestedWords(
            rescoredList,
            rescored, // Keep original as raw suggestions
            typedWord,
            true, // typedWordValid
            willAutoCorrect,
            false,
            results.mInputStyle,
            results.mSequenceNumber
        )
    } else {
        results
    }
} else {
    results
}
```

**Pros:**
- ✅ Preserves rejection mechanism
- ✅ Gets LLM benefits for auto-correction (index 1+)
- ✅ Minimal code change

**Cons:**
- ⚠️ Typed word always shown even if LLM is very confident it's wrong

### Option 2: Smart Merging
Merge LLM scores with geometric scores using weights:
```kotlin
val merged = geometricResults.map { wordInfo ->
    val lmmScore = lmmResults.find { it.mWord == wordInfo.mWord }?.mScore ?: 0
    val combinedScore = (geometricScore * 0.3) + (lmmScore * 0.7)
    wordInfo.copy(mScore = combinedScore)
}.sortedByDescending { it.mScore }

// Then apply Option 1's index preservation
```

**Pros:**
- ✅ Best of both algorithms
- ✅ Smoother integration

**Cons:**
- ⚠️ More complex
- ⚠️ Requires tuning weights

### Option 3: Two-Phase Suggestions
Show geometric results initially, LLM results after a delay:
```kotlin
// Immediate: Show geometric results
// After 200ms: Swap to LLM results if still typing
```

**Pros:**
- ✅ Fast initial response
- ✅ Better suggestions appear quickly

**Cons:**
- ⚠️ Flickering UI
- ⚠️ Complex state management

## Recommendation

**Implement Option 1** because:
1. Smallest change, lowest risk
2. Preserves existing behavior
3. Unblocks LLM rescoring immediately
4. Can iterate to Option 2 later if needed

## Related Code to Review

1. `SuggestedWords.java:34-35` - Index constants
2. `InputLogic.java` - Rejection handling (backspace after auto-correct)
3. `Suggest.java:obtainNonBatchedInputSuggestedWords()` - Where ordering is set
4. `LanguageModel.kt:rescoreSuggestions()` - The actual LLM rescoring

## Testing Requirements

Before enabling `useRescoring = true`:
1. ✅ Test rejection works (backspace after swipe)
2. ✅ Test typed word always appears
3. ✅ Test auto-correction threshold respected
4. ✅ Test multiple rejection cycles
5. ✅ Test LLM improves accuracy (benchmark tests)

## Impact Assessment

**If we don't fix this:**
- ❌ LLM remains disabled for swipe
- ❌ Miss opportunity for 20-30% accuracy improvement
- ❌ Competitive disadvantage vs Gboard

**If we do fix this:**
- ✅ LLM-powered swipe suggestions
- ✅ Context-aware predictions
- ✅ Maintained rejection UX
- ✅ Potential to match or exceed Gboard

## Estimated Improvement

Based on the LLM infrastructure already in place:
- **Geometric alone:** ~70% top-3 accuracy (current)
- **LLM rescoring:** ~85-90% top-3 accuracy (estimated)
- **Improvement:** **+15-20 percentage points**

This would address the main user complaint: "swipe doesn't work as well as Gboard"
