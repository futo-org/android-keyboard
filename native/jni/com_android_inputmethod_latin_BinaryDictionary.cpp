/*
 * Copyright (C) 2009, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


#include <cstring> // for memset()

#define LOG_TAG "LatinIME: jni: BinaryDictionary"

#include "defines.h" // for macros below

#ifdef USE_MMAP_FOR_DICTIONARY
#include <cerrno>
#include <fcntl.h>
#include <sys/mman.h>
#else // USE_MMAP_FOR_DICTIONARY
#include <cstdlib>
#include <cstdio> // for fopen() etc.
#endif // USE_MMAP_FOR_DICTIONARY

#include "binary_format.h"
#include "com_android_inputmethod_latin_BinaryDictionary.h"
#include "correction.h"
#include "dictionary.h"
#include "jni.h"
#include "jni_common.h"

namespace latinime {

class ProximityInfo;

static void releaseDictBuf(const void *dictBuf, const size_t length, const int fd);

static jlong latinime_BinaryDictionary_open(JNIEnv *env, jobject object,
        jstring sourceDir, jlong dictOffset, jlong dictSize,
        jint typedLetterMultiplier, jint fullWordMultiplier, jint maxWordLength, jint maxWords,
        jint maxPredictions) {
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
    int adjust = 0;
#ifdef USE_MMAP_FOR_DICTIONARY
    /* mmap version */
    fd = open(sourceDirChars, O_RDONLY);
    if (fd < 0) {
        AKLOGE("DICT: Can't open sourceDir. sourceDirChars=%s errno=%d", sourceDirChars, errno);
        return 0;
    }
    int pagesize = getpagesize();
    adjust = static_cast<int>(dictOffset) % pagesize;
    int adjDictOffset = static_cast<int>(dictOffset) - adjust;
    int adjDictSize = static_cast<int>(dictSize) + adjust;
    dictBuf = mmap(0, sizeof(char) * adjDictSize, PROT_READ, MAP_PRIVATE, fd, adjDictOffset);
    if (dictBuf == MAP_FAILED) {
        AKLOGE("DICT: Can't mmap dictionary. errno=%d", errno);
        return 0;
    }
    dictBuf = static_cast<char *>(dictBuf) + adjust;
#else // USE_MMAP_FOR_DICTIONARY
    /* malloc version */
    FILE *file = 0;
    file = fopen(sourceDirChars, "rb");
    if (file == 0) {
        AKLOGE("DICT: Can't fopen sourceDir. sourceDirChars=%s errno=%d", sourceDirChars, errno);
        return 0;
    }
    dictBuf = malloc(sizeof(char) * dictSize);
    if (!dictBuf) {
        AKLOGE("DICT: Can't allocate memory region for dictionary. errno=%d", errno);
        return 0;
    }
    int ret = fseek(file, (long)dictOffset, SEEK_SET);
    if (ret != 0) {
        AKLOGE("DICT: Failure in fseek. ret=%d errno=%d", ret, errno);
        return 0;
    }
    ret = fread(dictBuf, sizeof(char) * dictSize, 1, file);
    if (ret != 1) {
        AKLOGE("DICT: Failure in fread. ret=%d errno=%d", ret, errno);
        return 0;
    }
    ret = fclose(file);
    if (ret != 0) {
        AKLOGE("DICT: Failure in fclose. ret=%d errno=%d", ret, errno);
        return 0;
    }
#endif // USE_MMAP_FOR_DICTIONARY
    if (!dictBuf) {
        AKLOGE("DICT: dictBuf is null");
        return 0;
    }
    Dictionary *dictionary = 0;
    if (BinaryFormat::UNKNOWN_FORMAT
            == BinaryFormat::detectFormat(static_cast<uint8_t *>(dictBuf))) {
        AKLOGE("DICT: dictionary format is unknown, bad magic number");
#ifdef USE_MMAP_FOR_DICTIONARY
        releaseDictBuf(static_cast<const char *>(dictBuf) - adjust, adjDictSize, fd);
#else // USE_MMAP_FOR_DICTIONARY
        releaseDictBuf(dictBuf, 0, 0);
#endif // USE_MMAP_FOR_DICTIONARY
    } else {
        dictionary = new Dictionary(dictBuf, static_cast<int>(dictSize), fd, adjust,
                typedLetterMultiplier, fullWordMultiplier, maxWordLength, maxWords, maxPredictions);
    }
    PROF_END(66);
    PROF_CLOSE;
    return (jlong)dictionary;
}

static int latinime_BinaryDictionary_getSuggestions(JNIEnv *env, jobject object, jlong dict,
        jlong proximityInfo, jlong dicTraverseSession, jintArray xCoordinatesArray,
        jintArray yCoordinatesArray, jintArray timesArray, jintArray pointerIdsArray,
        jintArray inputCodePointsArray, jint arraySize, jint commitPoint, jboolean isGesture,
        jintArray prevWordCodePointsForBigrams, jboolean useFullEditDistance,
        jcharArray outputCharsArray, jintArray scoresArray, jintArray spaceIndicesArray,
        jintArray outputTypesArray) {
    Dictionary *dictionary = reinterpret_cast<Dictionary *>(dict);
    if (!dictionary) return 0;
    ProximityInfo *pInfo = reinterpret_cast<ProximityInfo *>(proximityInfo);
    void *traverseSession = reinterpret_cast<void *>(dicTraverseSession);

    // Input values
    int xCoordinates[arraySize];
    int yCoordinates[arraySize];
    int times[arraySize];
    int pointerIds[arraySize];
    const jsize inputCodePointsLength = env->GetArrayLength(inputCodePointsArray);
    int inputCodePoints[inputCodePointsLength];
    const jsize prevWordCodePointsLength =
            prevWordCodePointsForBigrams ? env->GetArrayLength(prevWordCodePointsForBigrams) : 0;
    int prevWordCodePointsInternal[prevWordCodePointsLength];
    int *prevWordCodePoints = 0;
    env->GetIntArrayRegion(xCoordinatesArray, 0, arraySize, xCoordinates);
    env->GetIntArrayRegion(yCoordinatesArray, 0, arraySize, yCoordinates);
    env->GetIntArrayRegion(timesArray, 0, arraySize, times);
    env->GetIntArrayRegion(pointerIdsArray, 0, arraySize, pointerIds);
    env->GetIntArrayRegion(inputCodePointsArray, 0, inputCodePointsLength, inputCodePoints);
    if (prevWordCodePointsForBigrams) {
        env->GetIntArrayRegion(prevWordCodePointsForBigrams, 0, prevWordCodePointsLength,
                prevWordCodePointsInternal);
        prevWordCodePoints = prevWordCodePointsInternal;
    }

    // Output values
    // TODO: Should be "outputCodePointsLength" and "int outputCodePoints[]"
    const jsize outputCharsLength = env->GetArrayLength(outputCharsArray);
    unsigned short outputChars[outputCharsLength];
    const jsize scoresLength = env->GetArrayLength(scoresArray);
    int scores[scoresLength];
    const jsize spaceIndicesLength = env->GetArrayLength(spaceIndicesArray);
    int spaceIndices[spaceIndicesLength];
    const jsize outputTypesLength = env->GetArrayLength(outputTypesArray);
    int outputTypes[outputTypesLength];
    memset(outputChars, 0, sizeof(outputChars));
    memset(scores, 0, sizeof(scores));
    memset(spaceIndices, 0, sizeof(spaceIndices));
    memset(outputTypes, 0, sizeof(outputTypes));

    int count;
    if (isGesture || arraySize > 0) {
        count = dictionary->getSuggestions(pInfo, traverseSession, xCoordinates, yCoordinates,
                times, pointerIds, inputCodePoints, arraySize, prevWordCodePoints,
                prevWordCodePointsLength, commitPoint, isGesture, useFullEditDistance, outputChars,
                scores, spaceIndices, outputTypes);
    } else {
        count = dictionary->getBigrams(prevWordCodePoints, prevWordCodePointsLength,
                inputCodePoints, arraySize, outputChars, scores, outputTypes);
    }

    // Copy back the output values
    // TODO: Should be SetIntArrayRegion()
    env->SetCharArrayRegion(outputCharsArray, 0, outputCharsLength, outputChars);
    env->SetIntArrayRegion(scoresArray, 0, scoresLength, scores);
    env->SetIntArrayRegion(spaceIndicesArray, 0, spaceIndicesLength, spaceIndices);
    env->SetIntArrayRegion(outputTypesArray, 0, outputTypesLength, outputTypes);

    return count;
}

static jint latinime_BinaryDictionary_getFrequency(JNIEnv *env, jobject object, jlong dict,
        jintArray wordArray) {
    Dictionary *dictionary = reinterpret_cast<Dictionary *>(dict);
    if (!dictionary) return 0;
    const jsize codePointLength = env->GetArrayLength(wordArray);
    int codePoints[codePointLength];
    env->GetIntArrayRegion(wordArray, 0, codePointLength, codePoints);
    return dictionary->getFrequency(codePoints, codePointLength);
}

static jboolean latinime_BinaryDictionary_isValidBigram(JNIEnv *env, jobject object, jlong dict,
        jintArray wordArray1, jintArray wordArray2) {
    Dictionary *dictionary = reinterpret_cast<Dictionary *>(dict);
    if (!dictionary) return (jboolean) false;
    const jsize codePointLength1 = env->GetArrayLength(wordArray1);
    const jsize codePointLength2 = env->GetArrayLength(wordArray2);
    int codePoints1[codePointLength1];
    int codePoints2[codePointLength2];
    env->GetIntArrayRegion(wordArray1, 0, codePointLength1, codePoints1);
    env->GetIntArrayRegion(wordArray2, 0, codePointLength2, codePoints2);
    return dictionary->isValidBigram(codePoints1, codePointLength1, codePoints2, codePointLength2);
}

static jfloat latinime_BinaryDictionary_calcNormalizedScore(JNIEnv *env, jobject object,
        jcharArray before, jcharArray after, jint score) {
    jsize beforeLength = env->GetArrayLength(before);
    jsize afterLength = env->GetArrayLength(after);
    jchar beforeChars[beforeLength];
    jchar afterChars[afterLength];
    env->GetCharArrayRegion(before, 0, beforeLength, beforeChars);
    env->GetCharArrayRegion(after, 0, afterLength, afterChars);
    return Correction::RankingAlgorithm::calcNormalizedScore(
            static_cast<unsigned short *>(beforeChars), beforeLength,
            static_cast<unsigned short *>(afterChars), afterLength, score);
}

static jint latinime_BinaryDictionary_editDistance(JNIEnv *env, jobject object,
        jcharArray before, jcharArray after) {
    jsize beforeLength = env->GetArrayLength(before);
    jsize afterLength = env->GetArrayLength(after);
    jchar beforeChars[beforeLength];
    jchar afterChars[afterLength];
    env->GetCharArrayRegion(before, 0, beforeLength, beforeChars);
    env->GetCharArrayRegion(after, 0, afterLength, afterChars);
    return Correction::RankingAlgorithm::editDistance(
            static_cast<unsigned short *>(beforeChars), beforeLength,
            static_cast<unsigned short *>(afterChars), afterLength);
}

static void latinime_BinaryDictionary_close(JNIEnv *env, jobject object, jlong dict) {
    Dictionary *dictionary = reinterpret_cast<Dictionary *>(dict);
    if (!dictionary) return;
    const void *dictBuf = dictionary->getDict();
    if (!dictBuf) return;
#ifdef USE_MMAP_FOR_DICTIONARY
    releaseDictBuf(static_cast<const char *>(dictBuf) - dictionary->getDictBufAdjust(),
            dictionary->getDictSize() + dictionary->getDictBufAdjust(), dictionary->getMmapFd());
#else // USE_MMAP_FOR_DICTIONARY
    releaseDictBuf(dictBuf, 0, 0);
#endif // USE_MMAP_FOR_DICTIONARY
    delete dictionary;
}

static void releaseDictBuf(const void *dictBuf, const size_t length, const int fd) {
#ifdef USE_MMAP_FOR_DICTIONARY
    int ret = munmap(const_cast<void *>(dictBuf), length);
    if (ret != 0) {
        AKLOGE("DICT: Failure in munmap. ret=%d errno=%d", ret, errno);
    }
    ret = close(fd);
    if (ret != 0) {
        AKLOGE("DICT: Failure in close. ret=%d errno=%d", ret, errno);
    }
#else // USE_MMAP_FOR_DICTIONARY
    free(const_cast<void *>(dictBuf));
#endif // USE_MMAP_FOR_DICTIONARY
}

static JNINativeMethod sMethods[] = {
    {"openNative", "(Ljava/lang/String;JJIIIII)J",
            reinterpret_cast<void *>(latinime_BinaryDictionary_open)},
    {"closeNative", "(J)V", reinterpret_cast<void *>(latinime_BinaryDictionary_close)},
    {"getSuggestionsNative", "(JJJ[I[I[I[I[IIIZ[IZ[C[I[I[I)I",
            reinterpret_cast<void *>(latinime_BinaryDictionary_getSuggestions)},
    {"getFrequencyNative", "(J[I)I",
            reinterpret_cast<void *>(latinime_BinaryDictionary_getFrequency)},
    {"isValidBigramNative", "(J[I[I)Z",
            reinterpret_cast<void *>(latinime_BinaryDictionary_isValidBigram)},
    {"calcNormalizedScoreNative", "([C[CI)F",
            reinterpret_cast<void *>(latinime_BinaryDictionary_calcNormalizedScore)},
    {"editDistanceNative", "([C[C)I",
            reinterpret_cast<void *>(latinime_BinaryDictionary_editDistance)}
};

int register_BinaryDictionary(JNIEnv *env) {
    const char *const kClassPathName = "com/android/inputmethod/latin/BinaryDictionary";
    return registerNativeMethods(env, kClassPathName, sMethods,
            sizeof(sMethods) / sizeof(sMethods[0]));
}
} // namespace latinime
