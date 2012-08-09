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

#define LOG_TAG "LatinIME: jni: BinaryDictionary"

#include "binary_format.h"
#include "com_android_inputmethod_latin_BinaryDictionary.h"
#include "correction.h"
#include "defines.h"
#include "dictionary.h"
#include "jni.h"
#include "jni_common.h"

#ifdef USE_MMAP_FOR_DICTIONARY
#include <cerrno>
#include <fcntl.h>
#include <sys/mman.h>
#else // USE_MMAP_FOR_DICTIONARY
#include <cstdlib>
#endif // USE_MMAP_FOR_DICTIONARY

namespace latinime {

class ProximityInfo;

static void releaseDictBuf(void *dictBuf, const size_t length, int fd);

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
    adjust = dictOffset % pagesize;
    int adjDictOffset = dictOffset - adjust;
    int adjDictSize = dictSize + adjust;
    dictBuf = mmap(0, sizeof(char) * adjDictSize, PROT_READ, MAP_PRIVATE, fd, adjDictOffset);
    if (dictBuf == MAP_FAILED) {
        AKLOGE("DICT: Can't mmap dictionary. errno=%d", errno);
        return 0;
    }
    dictBuf = (void *)((char *)dictBuf + adjust);
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
    if (BinaryFormat::UNKNOWN_FORMAT == BinaryFormat::detectFormat((uint8_t*)dictBuf)) {
        AKLOGE("DICT: dictionary format is unknown, bad magic number");
#ifdef USE_MMAP_FOR_DICTIONARY
        releaseDictBuf(((char*)dictBuf) - adjust, adjDictSize, fd);
#else // USE_MMAP_FOR_DICTIONARY
        releaseDictBuf(dictBuf, 0, 0);
#endif // USE_MMAP_FOR_DICTIONARY
    } else {
        dictionary = new Dictionary(dictBuf, dictSize, fd, adjust, typedLetterMultiplier,
                fullWordMultiplier, maxWordLength, maxWords, maxPredictions);
    }
    PROF_END(66);
    PROF_CLOSE;
    return (jlong)dictionary;
}

static int latinime_BinaryDictionary_getSuggestions(JNIEnv *env, jobject object, jlong dict,
        jlong proximityInfo, jlong dicTraverseSession, jintArray xCoordinatesArray,
        jintArray yCoordinatesArray, jintArray timesArray, jintArray pointerIdArray,
        jintArray inputArray, jint arraySize, jint commitPoint, jboolean isGesture,
        jintArray prevWordForBigrams, jboolean useFullEditDistance, jcharArray outputArray,
        jintArray frequencyArray, jintArray spaceIndexArray, jintArray outputTypesArray) {
    Dictionary *dictionary = reinterpret_cast<Dictionary*>(dict);
    if (!dictionary) return 0;
    ProximityInfo *pInfo = reinterpret_cast<ProximityInfo*>(proximityInfo);
    void *traverseSession = reinterpret_cast<void*>(dicTraverseSession);
    int *xCoordinates = env->GetIntArrayElements(xCoordinatesArray, 0);
    int *yCoordinates = env->GetIntArrayElements(yCoordinatesArray, 0);
    int *times = env->GetIntArrayElements(timesArray, 0);
    int *pointerIds = env->GetIntArrayElements(pointerIdArray, 0);
    int *frequencies = env->GetIntArrayElements(frequencyArray, 0);
    int *inputCodes = env->GetIntArrayElements(inputArray, 0);
    jchar *outputChars = env->GetCharArrayElements(outputArray, 0);
    int *spaceIndices = env->GetIntArrayElements(spaceIndexArray, 0);
    int *outputTypes = env->GetIntArrayElements(outputTypesArray, 0);
    jint *prevWordChars = prevWordForBigrams
            ? env->GetIntArrayElements(prevWordForBigrams, 0) : 0;
    jsize prevWordLength = prevWordChars ? env->GetArrayLength(prevWordForBigrams) : 0;

    int count;
    if (isGesture || arraySize > 1) {
        count = dictionary->getSuggestions(pInfo, traverseSession, xCoordinates, yCoordinates,
                times, pointerIds, inputCodes, arraySize, prevWordChars, prevWordLength,
                commitPoint, isGesture, useFullEditDistance, (unsigned short*) outputChars,
                frequencies, spaceIndices, outputTypes);
    } else {
        count = dictionary->getBigrams(prevWordChars, prevWordLength, inputCodes,
                arraySize, (unsigned short*) outputChars, frequencies, outputTypes);
    }

    if (prevWordChars) {
        env->ReleaseIntArrayElements(prevWordForBigrams, prevWordChars, JNI_ABORT);
    }
    env->ReleaseIntArrayElements(outputTypesArray, outputTypes, 0);
    env->ReleaseIntArrayElements(spaceIndexArray, spaceIndices, 0);
    env->ReleaseCharArrayElements(outputArray, outputChars, 0);
    env->ReleaseIntArrayElements(inputArray, inputCodes, JNI_ABORT);
    env->ReleaseIntArrayElements(frequencyArray, frequencies, 0);
    env->ReleaseIntArrayElements(pointerIdArray, pointerIds, 0);
    env->ReleaseIntArrayElements(timesArray, times, 0);
    env->ReleaseIntArrayElements(yCoordinatesArray, yCoordinates, 0);
    env->ReleaseIntArrayElements(xCoordinatesArray, xCoordinates, 0);
    return count;
}

static jint latinime_BinaryDictionary_getFrequency(JNIEnv *env, jobject object, jlong dict,
        jintArray wordArray) {
    Dictionary *dictionary = reinterpret_cast<Dictionary*>(dict);
    if (!dictionary) return 0;
    const jsize codePointLength = env->GetArrayLength(wordArray);
    int codePoints[codePointLength];
    env->GetIntArrayRegion(wordArray, 0, codePointLength, codePoints);
    return dictionary->getFrequency(codePoints, codePointLength);
}

static jboolean latinime_BinaryDictionary_isValidBigram(JNIEnv *env, jobject object, jlong dict,
        jintArray wordArray1, jintArray wordArray2) {
    Dictionary *dictionary = reinterpret_cast<Dictionary*>(dict);
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
    return Correction::RankingAlgorithm::calcNormalizedScore((unsigned short*)beforeChars,
            beforeLength, (unsigned short*)afterChars, afterLength, score);
}

static jint latinime_BinaryDictionary_editDistance(JNIEnv *env, jobject object,
        jcharArray before, jcharArray after) {
    jsize beforeLength = env->GetArrayLength(before);
    jsize afterLength = env->GetArrayLength(after);
    jchar beforeChars[beforeLength];
    jchar afterChars[afterLength];
    env->GetCharArrayRegion(before, 0, beforeLength, beforeChars);
    env->GetCharArrayRegion(after, 0, afterLength, afterChars);
    return Correction::RankingAlgorithm::editDistance((unsigned short*)beforeChars, beforeLength,
            (unsigned short*)afterChars, afterLength);
}

static void latinime_BinaryDictionary_close(JNIEnv *env, jobject object, jlong dict) {
    Dictionary *dictionary = reinterpret_cast<Dictionary*>(dict);
    if (!dictionary) return;
    void *dictBuf = dictionary->getDict();
    if (!dictBuf) return;
#ifdef USE_MMAP_FOR_DICTIONARY
    releaseDictBuf((void *)((char *)dictBuf - dictionary->getDictBufAdjust()),
            dictionary->getDictSize() + dictionary->getDictBufAdjust(), dictionary->getMmapFd());
#else // USE_MMAP_FOR_DICTIONARY
    releaseDictBuf(dictBuf, 0, 0);
#endif // USE_MMAP_FOR_DICTIONARY
    delete dictionary;
}

static void releaseDictBuf(void *dictBuf, const size_t length, int fd) {
#ifdef USE_MMAP_FOR_DICTIONARY
    int ret = munmap(dictBuf, length);
    if (ret != 0) {
        AKLOGE("DICT: Failure in munmap. ret=%d errno=%d", ret, errno);
    }
    ret = close(fd);
    if (ret != 0) {
        AKLOGE("DICT: Failure in close. ret=%d errno=%d", ret, errno);
    }
#else // USE_MMAP_FOR_DICTIONARY
    free(dictBuf);
#endif // USE_MMAP_FOR_DICTIONARY
}

static JNINativeMethod sMethods[] = {
    {"openNative", "(Ljava/lang/String;JJIIIII)J", (void*)latinime_BinaryDictionary_open},
    {"closeNative", "(J)V", (void*)latinime_BinaryDictionary_close},
    {"getSuggestionsNative", "(JJJ[I[I[I[I[IIIZ[IZ[C[I[I[I)I",
            (void*) latinime_BinaryDictionary_getSuggestions},
    {"getFrequencyNative", "(J[I)I", (void*)latinime_BinaryDictionary_getFrequency},
    {"isValidBigramNative", "(J[I[I)Z", (void*)latinime_BinaryDictionary_isValidBigram},
    {"calcNormalizedScoreNative", "([C[CI)F",
            (void*)latinime_BinaryDictionary_calcNormalizedScore},
    {"editDistanceNative", "([C[C)I", (void*)latinime_BinaryDictionary_editDistance}
};

int register_BinaryDictionary(JNIEnv *env) {
    const char *const kClassPathName = "com/android/inputmethod/latin/BinaryDictionary";
    return registerNativeMethods(env, kClassPathName, sMethods,
            sizeof(sMethods) / sizeof(sMethods[0]));
}
} // namespace latinime
