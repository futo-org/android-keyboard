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

#include <cerrno>
#include <cstring> // for memset()
#include <fcntl.h>
#include <sys/mman.h>
#include <unistd.h>

#include "defines.h"
#include "jni.h"
#include "jni_common.h"
#include "suggest/core/dictionary/binary_dictionary_format_utils.h"
#include "suggest/core/dictionary/binary_dictionary_info.h"
#include "suggest/core/dictionary/dictionary.h"
#include "suggest/core/suggest_options.h"
#include "utils/autocorrection_threshold_utils.h"

namespace latinime {

class ProximityInfo;

static void releaseDictBuf(const void *dictBuf, const size_t length, const int fd);

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
    int fd = 0;
    void *dictBuf = 0;
    int offset = 0;
    const bool updatableMmap = (isUpdatable == JNI_TRUE);
    const int openMode = updatableMmap ? O_RDWR : O_RDONLY;
    fd = open(sourceDirChars, openMode);
    if (fd < 0) {
        AKLOGE("DICT: Can't open sourceDir. sourceDirChars=%s errno=%d", sourceDirChars, errno);
        return 0;
    }
    int pagesize = getpagesize();
    offset = static_cast<int>(dictOffset) % pagesize;
    int adjDictOffset = static_cast<int>(dictOffset) - offset;
    int adjDictSize = static_cast<int>(dictSize) + offset;
    const int protMode = updatableMmap ? PROT_READ | PROT_WRITE : PROT_READ;
    dictBuf = mmap(0, adjDictSize, protMode, MAP_PRIVATE, fd, adjDictOffset);
    if (dictBuf == MAP_FAILED) {
        AKLOGE("DICT: Can't mmap dictionary. errno=%d", errno);
        return 0;
    }
    dictBuf = static_cast<char *>(dictBuf) + offset;
    if (!dictBuf) {
        AKLOGE("DICT: dictBuf is null");
        return 0;
    }
    Dictionary *dictionary = 0;
    if (BinaryDictionaryFormat::UNKNOWN_VERSION
            == BinaryDictionaryFormat::detectFormatVersion(static_cast<uint8_t *>(dictBuf),
                    static_cast<int>(dictSize))) {
        AKLOGE("DICT: dictionary format is unknown, bad magic number");
        releaseDictBuf(static_cast<const char *>(dictBuf) - offset, adjDictSize, fd);
    } else {
        dictionary = new Dictionary(
                dictBuf, static_cast<int>(dictSize), fd, offset, updatableMmap);
    }
    PROF_END(66);
    PROF_CLOSE;
    return reinterpret_cast<jlong>(dictionary);
}

static int latinime_BinaryDictionary_getSuggestions(JNIEnv *env, jclass clazz, jlong dict,
        jlong proximityInfo, jlong dicTraverseSession, jintArray xCoordinatesArray,
        jintArray yCoordinatesArray, jintArray timesArray, jintArray pointerIdsArray,
        jintArray inputCodePointsArray, jint inputSize, jint commitPoint, jintArray suggestOptions,
        jintArray prevWordCodePointsForBigrams, jintArray outputCodePointsArray,
        jintArray scoresArray, jintArray spaceIndicesArray, jintArray outputTypesArray) {
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
    memset(outputCodePoints, 0, sizeof(outputCodePoints));
    memset(scores, 0, sizeof(scores));
    memset(spaceIndices, 0, sizeof(spaceIndices));
    memset(outputTypes, 0, sizeof(outputTypes));

    int count;
    if (givenSuggestOptions.isGesture() || inputSize > 0) {
        count = dictionary->getSuggestions(pInfo, traverseSession, xCoordinates, yCoordinates,
                times, pointerIds, inputCodePoints, inputSize, prevWordCodePoints,
                prevWordCodePointsLength, commitPoint, &givenSuggestOptions, outputCodePoints,
                scores, spaceIndices, outputTypes);
    } else {
        count = dictionary->getBigrams(prevWordCodePoints, prevWordCodePointsLength,
                inputCodePoints, inputSize, outputCodePoints, scores, outputTypes);
    }

    // Copy back the output values
    env->SetIntArrayRegion(outputCodePointsArray, 0, outputCodePointsLength, outputCodePoints);
    env->SetIntArrayRegion(scoresArray, 0, scoresLength, scores);
    env->SetIntArrayRegion(spaceIndicesArray, 0, spaceIndicesLength, spaceIndices);
    env->SetIntArrayRegion(outputTypesArray, 0, outputTypesLength, outputTypes);

    return count;
}

static jint latinime_BinaryDictionary_getProbability(JNIEnv *env, jclass clazz, jlong dict,
        jintArray wordArray) {
    Dictionary *dictionary = reinterpret_cast<Dictionary *>(dict);
    if (!dictionary) return 0;
    const jsize codePointLength = env->GetArrayLength(wordArray);
    int codePoints[codePointLength];
    env->GetIntArrayRegion(wordArray, 0, codePointLength, codePoints);
    return dictionary->getProbability(codePoints, codePointLength);
}

static jboolean latinime_BinaryDictionary_isValidBigram(JNIEnv *env, jclass clazz, jlong dict,
        jintArray wordArray1, jintArray wordArray2) {
    Dictionary *dictionary = reinterpret_cast<Dictionary *>(dict);
    if (!dictionary) return JNI_FALSE;
    const jsize codePointLength1 = env->GetArrayLength(wordArray1);
    const jsize codePointLength2 = env->GetArrayLength(wordArray2);
    int codePoints1[codePointLength1];
    int codePoints2[codePointLength2];
    env->GetIntArrayRegion(wordArray1, 0, codePointLength1, codePoints1);
    env->GetIntArrayRegion(wordArray2, 0, codePointLength2, codePoints2);
    return dictionary->isValidBigram(codePoints1, codePointLength1, codePoints2, codePointLength2);
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

static void latinime_BinaryDictionary_close(JNIEnv *env, jclass clazz, jlong dict) {
    Dictionary *dictionary = reinterpret_cast<Dictionary *>(dict);
    if (!dictionary) return;
    const BinaryDictionaryInfo *const binaryDictionaryInfo = dictionary->getBinaryDictionaryInfo();
    const int dictBufOffset = binaryDictionaryInfo->getDictBufOffset();
    const void *dictBuf = binaryDictionaryInfo->getDictBuf();
    if (!dictBuf) return;
    releaseDictBuf(static_cast<const char *>(dictBuf) - dictBufOffset,
            binaryDictionaryInfo->getDictSize() + dictBufOffset,
            binaryDictionaryInfo->getMmapFd());
    delete dictionary;
}

static void releaseDictBuf(const void *dictBuf, const size_t length, const int fd) {
    int ret = munmap(const_cast<void *>(dictBuf), length);
    if (ret != 0) {
        AKLOGE("DICT: Failure in munmap. ret=%d errno=%d", ret, errno);
    }
    ret = close(fd);
    if (ret != 0) {
        AKLOGE("DICT: Failure in close. ret=%d errno=%d", ret, errno);
    }
}

static const JNINativeMethod sMethods[] = {
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
        const_cast<char *>("getSuggestionsNative"),
        const_cast<char *>("(JJJ[I[I[I[I[III[I[I[I[I[I[I)I"),
        reinterpret_cast<void *>(latinime_BinaryDictionary_getSuggestions)
    },
    {
        const_cast<char *>("getProbabilityNative"),
        const_cast<char *>("(J[I)I"),
        reinterpret_cast<void *>(latinime_BinaryDictionary_getProbability)
    },
    {
        const_cast<char *>("isValidBigramNative"),
        const_cast<char *>("(J[I[I)Z"),
        reinterpret_cast<void *>(latinime_BinaryDictionary_isValidBigram)
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
    }
};

int register_BinaryDictionary(JNIEnv *env) {
    const char *const kClassPathName = "com/android/inputmethod/latin/BinaryDictionary";
    return registerNativeMethods(env, kClassPathName, sMethods, NELEMS(sMethods));
}
} // namespace latinime
