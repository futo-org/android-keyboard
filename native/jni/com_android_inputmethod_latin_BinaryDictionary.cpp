/*
**
** Copyright 2009, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#define LOG_TAG "LatinIME: jni: BinaryDictionary"

#include "binary_format.h"
#include "correction.h"
#include "com_android_inputmethod_latin_BinaryDictionary.h"
#include "defines.h"
#include "dictionary.h"
#include "jni.h"
#include "jni_common.h"
#include "proximity_info.h"

#include <assert.h>
#include <errno.h>
#include <stdio.h>

#ifdef USE_MMAP_FOR_DICTIONARY
#include <sys/mman.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#else // USE_MMAP_FOR_DICTIONARY
#include <stdlib.h>
#endif // USE_MMAP_FOR_DICTIONARY

namespace latinime {

void releaseDictBuf(void* dictBuf, const size_t length, int fd);

static jlong latinime_BinaryDictionary_open(JNIEnv *env, jobject object,
        jstring sourceDir, jlong dictOffset, jlong dictSize,
        jint typedLetterMultiplier, jint fullWordMultiplier, jint maxWordLength, jint maxWords) {
    PROF_OPEN;
    PROF_START(66);
    const char *sourceDirChars = env->GetStringUTFChars(sourceDir, 0);
    if (sourceDirChars == 0) {
        AKLOGE("DICT: Can't get sourceDir string");
        return 0;
    }
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
    env->ReleaseStringUTFChars(sourceDir, sourceDirChars);

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
                fullWordMultiplier, maxWordLength, maxWords);
    }
    PROF_END(66);
    PROF_CLOSE;
    return (jlong)dictionary;
}

static int latinime_BinaryDictionary_getSuggestions(JNIEnv *env, jobject object, jlong dict,
        jlong proximityInfo, jintArray xCoordinatesArray, jintArray yCoordinatesArray,
        jintArray inputArray, jint arraySize, jintArray prevWordForBigrams,
        jboolean useFullEditDistance, jcharArray outputArray, jintArray frequencyArray) {
    Dictionary *dictionary = (Dictionary*)dict;
    if (!dictionary) return 0;
    ProximityInfo *pInfo = (ProximityInfo*)proximityInfo;
    int *xCoordinates = env->GetIntArrayElements(xCoordinatesArray, 0);
    int *yCoordinates = env->GetIntArrayElements(yCoordinatesArray, 0);
    int *frequencies = env->GetIntArrayElements(frequencyArray, 0);
    int *inputCodes = env->GetIntArrayElements(inputArray, 0);
    jchar *outputChars = env->GetCharArrayElements(outputArray, 0);
    jint *prevWordChars = prevWordForBigrams
            ? env->GetIntArrayElements(prevWordForBigrams, 0) : 0;
    jsize prevWordLength = prevWordChars ? env->GetArrayLength(prevWordForBigrams) : 0;
    int count = dictionary->getSuggestions(pInfo, xCoordinates, yCoordinates, inputCodes,
            arraySize, prevWordChars, prevWordLength, useFullEditDistance,
            (unsigned short*) outputChars, frequencies);
    if (prevWordChars) {
        env->ReleaseIntArrayElements(prevWordForBigrams, prevWordChars, JNI_ABORT);
    }
    env->ReleaseCharArrayElements(outputArray, outputChars, 0);
    env->ReleaseIntArrayElements(inputArray, inputCodes, JNI_ABORT);
    env->ReleaseIntArrayElements(frequencyArray, frequencies, 0);
    env->ReleaseIntArrayElements(yCoordinatesArray, yCoordinates, 0);
    env->ReleaseIntArrayElements(xCoordinatesArray, xCoordinates, 0);
    return count;
}

static int latinime_BinaryDictionary_getBigrams(JNIEnv *env, jobject object, jlong dict,
        jintArray prevWordArray, jint prevWordLength, jintArray inputArray, jint inputArraySize,
        jcharArray outputArray, jintArray frequencyArray, jint maxWordLength, jint maxBigrams) {
    Dictionary *dictionary = (Dictionary*)dict;
    if (!dictionary) return 0;
    jint *prevWord = env->GetIntArrayElements(prevWordArray, 0);
    int *inputCodes = env->GetIntArrayElements(inputArray, 0);
    jchar *outputChars = env->GetCharArrayElements(outputArray, 0);
    int *frequencies = env->GetIntArrayElements(frequencyArray, 0);
    int count = dictionary->getBigrams(prevWord, prevWordLength, inputCodes,
            inputArraySize, (unsigned short*) outputChars, frequencies, maxWordLength, maxBigrams);
    env->ReleaseIntArrayElements(frequencyArray, frequencies, 0);
    env->ReleaseCharArrayElements(outputArray, outputChars, 0);
    env->ReleaseIntArrayElements(inputArray, inputCodes, JNI_ABORT);
    env->ReleaseIntArrayElements(prevWordArray, prevWord, JNI_ABORT);
    return count;
}

static jint latinime_BinaryDictionary_getFrequency(JNIEnv *env, jobject object, jlong dict,
        jintArray wordArray, jint wordLength) {
    Dictionary *dictionary = (Dictionary*)dict;
    if (!dictionary) return (jboolean) false;
    jint *word = env->GetIntArrayElements(wordArray, 0);
    jint result = dictionary->getFrequency(word, wordLength);
    env->ReleaseIntArrayElements(wordArray, word, JNI_ABORT);
    return result;
}

static jboolean latinime_BinaryDictionary_isValidBigram(JNIEnv *env, jobject object, jlong dict,
        jintArray wordArray1, jintArray wordArray2) {
    Dictionary *dictionary = (Dictionary*)dict;
    if (!dictionary) return (jboolean) false;
    jint *word1 = env->GetIntArrayElements(wordArray1, 0);
    jint *word2 = env->GetIntArrayElements(wordArray2, 0);
    jsize length1 = word1 ? env->GetArrayLength(wordArray1) : 0;
    jsize length2 = word2 ? env->GetArrayLength(wordArray2) : 0;
    jboolean result = dictionary->isValidBigram(word1, length1, word2, length2);
    env->ReleaseIntArrayElements(wordArray2, word2, JNI_ABORT);
    env->ReleaseIntArrayElements(wordArray1, word1, JNI_ABORT);
    return result;
}

static jfloat latinime_BinaryDictionary_calcNormalizedScore(JNIEnv *env, jobject object,
        jcharArray before, jint beforeLength, jcharArray after, jint afterLength, jint score) {
    jchar *beforeChars = env->GetCharArrayElements(before, 0);
    jchar *afterChars = env->GetCharArrayElements(after, 0);
    jfloat result = Correction::RankingAlgorithm::calcNormalizedScore((unsigned short*)beforeChars,
            beforeLength, (unsigned short*)afterChars, afterLength, score);
    env->ReleaseCharArrayElements(after, afterChars, JNI_ABORT);
    env->ReleaseCharArrayElements(before, beforeChars, JNI_ABORT);
    return result;
}

static jint latinime_BinaryDictionary_editDistance(JNIEnv *env, jobject object,
        jcharArray before, jint beforeLength, jcharArray after, jint afterLength) {
    jchar *beforeChars = env->GetCharArrayElements(before, 0);
    jchar *afterChars = env->GetCharArrayElements(after, 0);
    jint result = Correction::RankingAlgorithm::editDistance(
            (unsigned short*)beforeChars, beforeLength, (unsigned short*)afterChars, afterLength);
    env->ReleaseCharArrayElements(after, afterChars, JNI_ABORT);
    env->ReleaseCharArrayElements(before, beforeChars, JNI_ABORT);
    return result;
}

static void latinime_BinaryDictionary_close(JNIEnv *env, jobject object, jlong dict) {
    Dictionary *dictionary = (Dictionary*)dict;
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

void releaseDictBuf(void* dictBuf, const size_t length, int fd) {
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
    {"openNative", "(Ljava/lang/String;JJIIII)J", (void*)latinime_BinaryDictionary_open},
    {"closeNative", "(J)V", (void*)latinime_BinaryDictionary_close},
    {"getSuggestionsNative", "(JJ[I[I[II[IZ[C[I)I",
            (void*)latinime_BinaryDictionary_getSuggestions},
    {"getFrequencyNative", "(J[II)I", (void*)latinime_BinaryDictionary_getFrequency},
    {"isValidBigramNative", "(J[I[I)Z", (void*)latinime_BinaryDictionary_isValidBigram},
    {"getBigramsNative", "(J[II[II[C[III)I", (void*)latinime_BinaryDictionary_getBigrams},
    {"calcNormalizedScoreNative", "([CI[CII)F",
            (void*)latinime_BinaryDictionary_calcNormalizedScore},
    {"editDistanceNative", "([CI[CI)I", (void*)latinime_BinaryDictionary_editDistance}
};

int register_BinaryDictionary(JNIEnv *env) {
    const char* const kClassPathName = "com/android/inputmethod/latin/BinaryDictionary";
    return registerNativeMethods(env, kClassPathName, sMethods,
            sizeof(sMethods) / sizeof(sMethods[0]));
}

} // namespace latinime
