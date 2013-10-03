/*
 * Copyright (C) 2009 The Android Open Source Project
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

#define LOG_TAG "LatinIME: jni: BinaryDictionary"

#include "com_android_inputmethod_latin_BinaryDictionary.h"

#include <cstring> // for memset()

#include "defines.h"
#include "jni.h"
#include "jni_common.h"
#include "suggest/core/dictionary/dictionary.h"
#include "suggest/core/suggest_options.h"
#include "suggest/policyimpl/dictionary/dictionary_structure_with_buffer_policy_factory.h"
#include "suggest/policyimpl/dictionary/utils/dict_file_writing_utils.h"
#include "utils/autocorrection_threshold_utils.h"

namespace latinime {

class ProximityInfo;

// TODO: Move to makedict.
static jboolean latinime_BinaryDictionary_createEmptyDictFile(JNIEnv *env, jclass clazz,
        jstring filePath, jlong dictVersion, jobjectArray attributeKeyStringArray,
        jobjectArray attributeValueStringArray) {
    const jsize filePathUtf8Length = env->GetStringUTFLength(filePath);
    char filePathChars[filePathUtf8Length + 1];
    env->GetStringUTFRegion(filePath, 0, env->GetStringLength(filePath), filePathChars);
    filePathChars[filePathUtf8Length] = '\0';

    const int keyCount = env->GetArrayLength(attributeKeyStringArray);
    const int valueCount = env->GetArrayLength(attributeValueStringArray);
    if (keyCount != valueCount) {
        return false;
    }

    HeaderReadWriteUtils::AttributeMap attributeMap;
    for (int i = 0; i < keyCount; i++) {
        jstring keyString = static_cast<jstring>(
                env->GetObjectArrayElement(attributeKeyStringArray, i));
        const jsize keyUtf8Length = env->GetStringUTFLength(keyString);
        char keyChars[keyUtf8Length + 1];
        env->GetStringUTFRegion(keyString, 0, env->GetStringLength(keyString), keyChars);
        keyChars[keyUtf8Length] = '\0';
        HeaderReadWriteUtils::AttributeMap::key_type key;
        HeaderReadWriteUtils::insertCharactersIntoVector(keyChars, &key);

        jstring valueString = static_cast<jstring>(
                env->GetObjectArrayElement(attributeValueStringArray, i));
        const jsize valueUtf8Length = env->GetStringUTFLength(valueString);
        char valueChars[valueUtf8Length + 1];
        env->GetStringUTFRegion(valueString, 0, env->GetStringLength(valueString), valueChars);
        valueChars[valueUtf8Length] = '\0';
        HeaderReadWriteUtils::AttributeMap::mapped_type value;
        HeaderReadWriteUtils::insertCharactersIntoVector(valueChars, &value);
        attributeMap[key] = value;
    }

    return DictFileWritingUtils::createEmptyDictFile(filePathChars, static_cast<int>(dictVersion),
            &attributeMap);
}

static jlong latinime_BinaryDictionary_open(JNIEnv *env, jclass clazz, jstring sourceDir,
        jlong dictOffset, jlong dictSize, jboolean isUpdatable) {
    PROF_OPEN;
    PROF_START(66);
    const jsize sourceDirUtf8Length = env->GetStringUTFLength(sourceDir);
    if (sourceDirUtf8Length <= 0) {
        AKLOGE("DICT: Can't get sourceDir string");
        return 0;
    }
    char sourceDirChars[sourceDirUtf8Length + 1];
    env->GetStringUTFRegion(sourceDir, 0, env->GetStringLength(sourceDir), sourceDirChars);
    sourceDirChars[sourceDirUtf8Length] = '\0';
    DictionaryStructureWithBufferPolicy *const dictionaryStructureWithBufferPolicy =
            DictionaryStructureWithBufferPolicyFactory::newDictionaryStructureWithBufferPolicy(
                    sourceDirChars, static_cast<int>(dictOffset), static_cast<int>(dictSize),
                    isUpdatable == JNI_TRUE);
    if (!dictionaryStructureWithBufferPolicy) {
        return 0;
    }

    Dictionary *const dictionary = new Dictionary(env, dictionaryStructureWithBufferPolicy);
    PROF_END(66);
    PROF_CLOSE;
    return reinterpret_cast<jlong>(dictionary);
}

static void latinime_BinaryDictionary_flush(JNIEnv *env, jclass clazz, jlong dict,
        jstring filePath) {
    Dictionary *dictionary = reinterpret_cast<Dictionary *>(dict);
    if (!dictionary) return;
    const jsize filePathUtf8Length = env->GetStringUTFLength(filePath);
    char filePathChars[filePathUtf8Length + 1];
    env->GetStringUTFRegion(filePath, 0, env->GetStringLength(filePath), filePathChars);
    filePathChars[filePathUtf8Length] = '\0';
    dictionary->flush(filePathChars);
}

static bool latinime_BinaryDictionary_needsToRunGC(JNIEnv *env, jclass clazz,
        jlong dict, jboolean mindsBlockByGC) {
    Dictionary *dictionary = reinterpret_cast<Dictionary *>(dict);
    if (!dictionary) return false;
    return dictionary->needsToRunGC(mindsBlockByGC == JNI_TRUE);
}

static void latinime_BinaryDictionary_flushWithGC(JNIEnv *env, jclass clazz, jlong dict,
        jstring filePath) {
    Dictionary *dictionary = reinterpret_cast<Dictionary *>(dict);
    if (!dictionary) return;
    const jsize filePathUtf8Length = env->GetStringUTFLength(filePath);
    char filePathChars[filePathUtf8Length + 1];
    env->GetStringUTFRegion(filePath, 0, env->GetStringLength(filePath), filePathChars);
    filePathChars[filePathUtf8Length] = '\0';
    dictionary->flushWithGC(filePathChars);
}

static void latinime_BinaryDictionary_close(JNIEnv *env, jclass clazz, jlong dict) {
    Dictionary *dictionary = reinterpret_cast<Dictionary *>(dict);
    if (!dictionary) return;
    delete dictionary;
}

static int latinime_BinaryDictionary_getSuggestions(JNIEnv *env, jclass clazz, jlong dict,
        jlong proximityInfo, jlong dicTraverseSession, jintArray xCoordinatesArray,
        jintArray yCoordinatesArray, jintArray timesArray, jintArray pointerIdsArray,
        jintArray inputCodePointsArray, jint inputSize, jint commitPoint, jintArray suggestOptions,
        jintArray prevWordCodePointsForBigrams, jintArray outputCodePointsArray,
        jintArray scoresArray, jintArray spaceIndicesArray, jintArray outputTypesArray,
        jintArray outputAutoCommitFirstWordConfidenceArray) {
    Dictionary *dictionary = reinterpret_cast<Dictionary *>(dict);
    if (!dictionary) return 0;
    ProximityInfo *pInfo = reinterpret_cast<ProximityInfo *>(proximityInfo);
    DicTraverseSession *traverseSession =
            reinterpret_cast<DicTraverseSession *>(dicTraverseSession);

    // Input values
    int xCoordinates[inputSize];
    int yCoordinates[inputSize];
    int times[inputSize];
    int pointerIds[inputSize];
    const jsize inputCodePointsLength = env->GetArrayLength(inputCodePointsArray);
    int inputCodePoints[inputCodePointsLength];
    const jsize prevWordCodePointsLength =
            prevWordCodePointsForBigrams ? env->GetArrayLength(prevWordCodePointsForBigrams) : 0;
    int prevWordCodePointsInternal[prevWordCodePointsLength];
    int *prevWordCodePoints = 0;
    env->GetIntArrayRegion(xCoordinatesArray, 0, inputSize, xCoordinates);
    env->GetIntArrayRegion(yCoordinatesArray, 0, inputSize, yCoordinates);
    env->GetIntArrayRegion(timesArray, 0, inputSize, times);
    env->GetIntArrayRegion(pointerIdsArray, 0, inputSize, pointerIds);
    env->GetIntArrayRegion(inputCodePointsArray, 0, inputCodePointsLength, inputCodePoints);
    if (prevWordCodePointsForBigrams) {
        env->GetIntArrayRegion(prevWordCodePointsForBigrams, 0, prevWordCodePointsLength,
                prevWordCodePointsInternal);
        prevWordCodePoints = prevWordCodePointsInternal;
    }

    const jsize numberOfOptions = env->GetArrayLength(suggestOptions);
    int options[numberOfOptions];
    env->GetIntArrayRegion(suggestOptions, 0, numberOfOptions, options);
    SuggestOptions givenSuggestOptions(options, numberOfOptions);

    // Output values
    /* By the way, let's check the output array length here to make sure */
    const jsize outputCodePointsLength = env->GetArrayLength(outputCodePointsArray);
    if (outputCodePointsLength != (MAX_WORD_LENGTH * MAX_RESULTS)) {
        AKLOGE("Invalid outputCodePointsLength: %d", outputCodePointsLength);
        ASSERT(false);
        return 0;
    }
    const jsize scoresLength = env->GetArrayLength(scoresArray);
    if (scoresLength != MAX_RESULTS) {
        AKLOGE("Invalid scoresLength: %d", scoresLength);
        ASSERT(false);
        return 0;
    }
    int outputCodePoints[outputCodePointsLength];
    int scores[scoresLength];
    const jsize spaceIndicesLength = env->GetArrayLength(spaceIndicesArray);
    int spaceIndices[spaceIndicesLength];
    const jsize outputTypesLength = env->GetArrayLength(outputTypesArray);
    int outputTypes[outputTypesLength];
    const jsize outputAutoCommitFirstWordConfidenceLength =
            env->GetArrayLength(outputAutoCommitFirstWordConfidenceArray);
    // We only use the first result, as obviously we will only ever autocommit the first one
    ASSERT(outputAutoCommitFirstWordConfidenceLength == 1);
    int outputAutoCommitFirstWordConfidence[outputAutoCommitFirstWordConfidenceLength];
    memset(outputCodePoints, 0, sizeof(outputCodePoints));
    memset(scores, 0, sizeof(scores));
    memset(spaceIndices, 0, sizeof(spaceIndices));
    memset(outputTypes, 0, sizeof(outputTypes));
    memset(outputAutoCommitFirstWordConfidence, 0, sizeof(outputAutoCommitFirstWordConfidence));

    int count;
    if (givenSuggestOptions.isGesture() || inputSize > 0) {
        count = dictionary->getSuggestions(pInfo, traverseSession, xCoordinates, yCoordinates,
                times, pointerIds, inputCodePoints, inputSize, prevWordCodePoints,
                prevWordCodePointsLength, commitPoint, &givenSuggestOptions, outputCodePoints,
                scores, spaceIndices, outputTypes, outputAutoCommitFirstWordConfidence);
    } else {
        count = dictionary->getBigrams(prevWordCodePoints, prevWordCodePointsLength,
                outputCodePoints, scores, outputTypes);
    }

    // Copy back the output values
    env->SetIntArrayRegion(outputCodePointsArray, 0, outputCodePointsLength, outputCodePoints);
    env->SetIntArrayRegion(scoresArray, 0, scoresLength, scores);
    env->SetIntArrayRegion(spaceIndicesArray, 0, spaceIndicesLength, spaceIndices);
    env->SetIntArrayRegion(outputTypesArray, 0, outputTypesLength, outputTypes);
    env->SetIntArrayRegion(outputAutoCommitFirstWordConfidenceArray, 0,
            outputAutoCommitFirstWordConfidenceLength, outputAutoCommitFirstWordConfidence);

    return count;
}

static jint latinime_BinaryDictionary_getProbability(JNIEnv *env, jclass clazz, jlong dict,
        jintArray word) {
    Dictionary *dictionary = reinterpret_cast<Dictionary *>(dict);
    if (!dictionary) return NOT_A_PROBABILITY;
    const jsize wordLength = env->GetArrayLength(word);
    int codePoints[wordLength];
    env->GetIntArrayRegion(word, 0, wordLength, codePoints);
    return dictionary->getProbability(codePoints, wordLength);
}

static jint latinime_BinaryDictionary_getBigramProbability(JNIEnv *env, jclass clazz,
        jlong dict, jintArray word0, jintArray word1) {
    Dictionary *dictionary = reinterpret_cast<Dictionary *>(dict);
    if (!dictionary) return JNI_FALSE;
    const jsize word0Length = env->GetArrayLength(word0);
    const jsize word1Length = env->GetArrayLength(word1);
    int word0CodePoints[word0Length];
    int word1CodePoints[word1Length];
    env->GetIntArrayRegion(word0, 0, word0Length, word0CodePoints);
    env->GetIntArrayRegion(word1, 0, word1Length, word1CodePoints);
    return dictionary->getBigramProbability(word0CodePoints, word0Length, word1CodePoints,
            word1Length);
}

static jfloat latinime_BinaryDictionary_calcNormalizedScore(JNIEnv *env, jclass clazz,
        jintArray before, jintArray after, jint score) {
    jsize beforeLength = env->GetArrayLength(before);
    jsize afterLength = env->GetArrayLength(after);
    int beforeCodePoints[beforeLength];
    int afterCodePoints[afterLength];
    env->GetIntArrayRegion(before, 0, beforeLength, beforeCodePoints);
    env->GetIntArrayRegion(after, 0, afterLength, afterCodePoints);
    return AutocorrectionThresholdUtils::calcNormalizedScore(beforeCodePoints, beforeLength,
            afterCodePoints, afterLength, score);
}

static jint latinime_BinaryDictionary_editDistance(JNIEnv *env, jclass clazz, jintArray before,
        jintArray after) {
    jsize beforeLength = env->GetArrayLength(before);
    jsize afterLength = env->GetArrayLength(after);
    int beforeCodePoints[beforeLength];
    int afterCodePoints[afterLength];
    env->GetIntArrayRegion(before, 0, beforeLength, beforeCodePoints);
    env->GetIntArrayRegion(after, 0, afterLength, afterCodePoints);
    return AutocorrectionThresholdUtils::editDistance(beforeCodePoints, beforeLength,
            afterCodePoints, afterLength);
}

static void latinime_BinaryDictionary_addUnigramWord(JNIEnv *env, jclass clazz, jlong dict,
        jintArray word, jint probability) {
    Dictionary *dictionary = reinterpret_cast<Dictionary *>(dict);
    if (!dictionary) {
        return;
    }
    jsize wordLength = env->GetArrayLength(word);
    int codePoints[wordLength];
    env->GetIntArrayRegion(word, 0, wordLength, codePoints);
    dictionary->addUnigramWord(codePoints, wordLength, probability);
}

static void latinime_BinaryDictionary_addBigramWords(JNIEnv *env, jclass clazz, jlong dict,
        jintArray word0, jintArray word1, jint probability) {
    Dictionary *dictionary = reinterpret_cast<Dictionary *>(dict);
    if (!dictionary) {
        return;
    }
    jsize word0Length = env->GetArrayLength(word0);
    int word0CodePoints[word0Length];
    env->GetIntArrayRegion(word0, 0, word0Length, word0CodePoints);
    jsize word1Length = env->GetArrayLength(word1);
    int word1CodePoints[word1Length];
    env->GetIntArrayRegion(word1, 0, word1Length, word1CodePoints);
    dictionary->addBigramWords(word0CodePoints, word0Length, word1CodePoints,
            word1Length, probability);
}

static void latinime_BinaryDictionary_removeBigramWords(JNIEnv *env, jclass clazz, jlong dict,
        jintArray word0, jintArray word1) {
    Dictionary *dictionary = reinterpret_cast<Dictionary *>(dict);
    if (!dictionary) {
        return;
    }
    jsize word0Length = env->GetArrayLength(word0);
    int word0CodePoints[word0Length];
    env->GetIntArrayRegion(word0, 0, word0Length, word0CodePoints);
    jsize word1Length = env->GetArrayLength(word1);
    int word1CodePoints[word1Length];
    env->GetIntArrayRegion(word1, 0, word1Length, word1CodePoints);
    dictionary->removeBigramWords(word0CodePoints, word0Length, word1CodePoints,
            word1Length);
}

static int latinime_BinaryDictionary_calculateProbabilityNative(JNIEnv *env, jclass clazz,
        jlong dict, jint unigramProbability, jint bigramProbability) {
    Dictionary *dictionary = reinterpret_cast<Dictionary *>(dict);
    if (!dictionary) {
        return NOT_A_PROBABILITY;
    }
    return dictionary->getDictionaryStructurePolicy()->getProbability(unigramProbability,
            bigramProbability);
}

static jstring latinime_BinaryDictionary_getProperty(JNIEnv *env, jclass clazz, jlong dict,
        jstring query) {
    Dictionary *dictionary = reinterpret_cast<Dictionary *>(dict);
    if (!dictionary) {
        return env->NewStringUTF("");
    }
    const jsize queryUtf8Length = env->GetStringUTFLength(query);
    char queryChars[queryUtf8Length + 1];
    env->GetStringUTFRegion(query, 0, env->GetStringLength(query), queryChars);
    queryChars[queryUtf8Length] = '\0';
    static const int GET_PROPERTY_RESULT_LENGTH = 100;
    char resultChars[GET_PROPERTY_RESULT_LENGTH];
    resultChars[0] = '\0';
    dictionary->getProperty(queryChars, resultChars, GET_PROPERTY_RESULT_LENGTH);
    return env->NewStringUTF(resultChars);
}

static const JNINativeMethod sMethods[] = {
    {
        const_cast<char *>("createEmptyDictFileNative"),
        const_cast<char *>("(Ljava/lang/String;J[Ljava/lang/String;[Ljava/lang/String;)Z"),
        reinterpret_cast<void *>(latinime_BinaryDictionary_createEmptyDictFile)
    },
    {
        const_cast<char *>("openNative"),
        const_cast<char *>("(Ljava/lang/String;JJZ)J"),
        reinterpret_cast<void *>(latinime_BinaryDictionary_open)
    },
    {
        const_cast<char *>("closeNative"),
        const_cast<char *>("(J)V"),
        reinterpret_cast<void *>(latinime_BinaryDictionary_close)
    },
    {
        const_cast<char *>("flushNative"),
        const_cast<char *>("(JLjava/lang/String;)V"),
        reinterpret_cast<void *>(latinime_BinaryDictionary_flush)
    },
    {
        const_cast<char *>("needsToRunGCNative"),
        const_cast<char *>("(JZ)Z"),
        reinterpret_cast<void *>(latinime_BinaryDictionary_needsToRunGC)
    },
    {
        const_cast<char *>("flushWithGCNative"),
        const_cast<char *>("(JLjava/lang/String;)V"),
        reinterpret_cast<void *>(latinime_BinaryDictionary_flushWithGC)
    },
    {
        const_cast<char *>("getSuggestionsNative"),
        const_cast<char *>("(JJJ[I[I[I[I[III[I[I[I[I[I[I[I)I"),
        reinterpret_cast<void *>(latinime_BinaryDictionary_getSuggestions)
    },
    {
        const_cast<char *>("getProbabilityNative"),
        const_cast<char *>("(J[I)I"),
        reinterpret_cast<void *>(latinime_BinaryDictionary_getProbability)
    },
    {
        const_cast<char *>("getBigramProbabilityNative"),
        const_cast<char *>("(J[I[I)I"),
        reinterpret_cast<void *>(latinime_BinaryDictionary_getBigramProbability)
    },
    {
        const_cast<char *>("calcNormalizedScoreNative"),
        const_cast<char *>("([I[II)F"),
        reinterpret_cast<void *>(latinime_BinaryDictionary_calcNormalizedScore)
    },
    {
        const_cast<char *>("editDistanceNative"),
        const_cast<char *>("([I[I)I"),
        reinterpret_cast<void *>(latinime_BinaryDictionary_editDistance)
    },
    {
        const_cast<char *>("addUnigramWordNative"),
        const_cast<char *>("(J[II)V"),
        reinterpret_cast<void *>(latinime_BinaryDictionary_addUnigramWord)
    },
    {
        const_cast<char *>("addBigramWordsNative"),
        const_cast<char *>("(J[I[II)V"),
        reinterpret_cast<void *>(latinime_BinaryDictionary_addBigramWords)
    },
    {
        const_cast<char *>("removeBigramWordsNative"),
        const_cast<char *>("(J[I[I)V"),
        reinterpret_cast<void *>(latinime_BinaryDictionary_removeBigramWords)
    },
    {
        const_cast<char *>("calculateProbabilityNative"),
        const_cast<char *>("(JII)I"),
        reinterpret_cast<void *>(latinime_BinaryDictionary_calculateProbabilityNative)
    },
    {
        const_cast<char *>("getPropertyNative"),
        const_cast<char *>("(JLjava/lang/String;)Ljava/lang/String;"),
        reinterpret_cast<void *>(latinime_BinaryDictionary_getProperty)
    }
};

int register_BinaryDictionary(JNIEnv *env) {
    const char *const kClassPathName = "com/android/inputmethod/latin/BinaryDictionary";
    return registerNativeMethods(env, kClassPathName, sMethods, NELEMS(sMethods));
}
} // namespace latinime
