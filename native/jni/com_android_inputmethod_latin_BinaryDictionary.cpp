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
#include "com_android_inputmethod_latin_BinaryDictionary.h"
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
#else // USE_MMAP_FOR_DICTIONARY
#include <stdlib.h>
#endif // USE_MMAP_FOR_DICTIONARY

namespace latinime {

void releaseDictBuf(void* dictBuf, const size_t length, int fd);

static jint latinime_BinaryDictionary_open(JNIEnv *env, jobject object,
        jstring sourceDir, jlong dictOffset, jlong dictSize,
        jint typedLetterMultiplier, jint fullWordMultiplier, jint maxWordLength, jint maxWords,
        jint maxAlternatives) {
    PROF_OPEN;
    PROF_START(66);
    const char *sourceDirChars = env->GetStringUTFChars(sourceDir, NULL);
    if (sourceDirChars == NULL) {
        LOGE("DICT: Can't get sourceDir string");
        return 0;
    }
    int fd = 0;
    void *dictBuf = NULL;
    int adjust = 0;
#ifdef USE_MMAP_FOR_DICTIONARY
    /* mmap version */
    fd = open(sourceDirChars, O_RDONLY);
    if (fd < 0) {
        LOGE("DICT: Can't open sourceDir. sourceDirChars=%s errno=%d", sourceDirChars, errno);
        return 0;
    }
    int pagesize = getpagesize();
    adjust = dictOffset % pagesize;
    int adjDictOffset = dictOffset - adjust;
    int adjDictSize = dictSize + adjust;
    dictBuf = mmap(NULL, sizeof(char) * adjDictSize, PROT_READ, MAP_PRIVATE, fd, adjDictOffset);
    if (dictBuf == MAP_FAILED) {
        LOGE("DICT: Can't mmap dictionary. errno=%d", errno);
        return 0;
    }
    dictBuf = (void *)((char *)dictBuf + adjust);
#else // USE_MMAP_FOR_DICTIONARY
    /* malloc version */
    FILE *file = NULL;
    file = fopen(sourceDirChars, "rb");
    if (file == NULL) {
        LOGE("DICT: Can't fopen sourceDir. sourceDirChars=%s errno=%d", sourceDirChars, errno);
        return 0;
    }
    dictBuf = malloc(sizeof(char) * dictSize);
    if (!dictBuf) {
        LOGE("DICT: Can't allocate memory region for dictionary. errno=%d", errno);
        return 0;
    }
    int ret = fseek(file, (long)dictOffset, SEEK_SET);
    if (ret != 0) {
        LOGE("DICT: Failure in fseek. ret=%d errno=%d", ret, errno);
        return 0;
    }
    ret = fread(dictBuf, sizeof(char) * dictSize, 1, file);
    if (ret != 1) {
        LOGE("DICT: Failure in fread. ret=%d errno=%d", ret, errno);
        return 0;
    }
    ret = fclose(file);
    if (ret != 0) {
        LOGE("DICT: Failure in fclose. ret=%d errno=%d", ret, errno);
        return 0;
    }
#endif // USE_MMAP_FOR_DICTIONARY
    env->ReleaseStringUTFChars(sourceDir, sourceDirChars);

    if (!dictBuf) {
        LOGE("DICT: dictBuf is null");
        return 0;
    }
    Dictionary *dictionary = NULL;
    if (BinaryFormat::UNKNOWN_FORMAT == BinaryFormat::detectFormat((uint8_t*)dictBuf)) {
        LOGE("DICT: dictionary format is unknown, bad magic number");
#ifdef USE_MMAP_FOR_DICTIONARY
        releaseDictBuf(((char*)dictBuf) - adjust, adjDictSize, fd);
#else // USE_MMAP_FOR_DICTIONARY
        releaseDictBuf(dictBuf, 0, 0);
#endif // USE_MMAP_FOR_DICTIONARY
    } else {
        dictionary = new Dictionary(dictBuf, dictSize, fd, adjust, typedLetterMultiplier,
                fullWordMultiplier, maxWordLength, maxWords, maxAlternatives);
    }
    PROF_END(66);
    PROF_CLOSE;
    return (jint)dictionary;
}

static int latinime_BinaryDictionary_getSuggestions(JNIEnv *env, jobject object, jint dict,
        jint proximityInfo, jintArray xCoordinatesArray, jintArray yCoordinatesArray,
        jintArray inputArray, jint arraySize, jint flags,
        jcharArray outputArray, jintArray frequencyArray) {
    Dictionary *dictionary = (Dictionary*)dict;
    if (!dictionary) return 0;
    ProximityInfo *pInfo = (ProximityInfo*)proximityInfo;

    int *xCoordinates = env->GetIntArrayElements(xCoordinatesArray, NULL);
    int *yCoordinates = env->GetIntArrayElements(yCoordinatesArray, NULL);

    int *frequencies = env->GetIntArrayElements(frequencyArray, NULL);
    int *inputCodes = env->GetIntArrayElements(inputArray, NULL);
    jchar *outputChars = env->GetCharArrayElements(outputArray, NULL);

    int count = dictionary->getSuggestions(pInfo, xCoordinates, yCoordinates, inputCodes,
            arraySize, flags, (unsigned short*) outputChars, frequencies);

    env->ReleaseIntArrayElements(frequencyArray, frequencies, 0);
    env->ReleaseIntArrayElements(inputArray, inputCodes, JNI_ABORT);
    env->ReleaseIntArrayElements(xCoordinatesArray, xCoordinates, 0);
    env->ReleaseIntArrayElements(yCoordinatesArray, yCoordinates, 0);
    env->ReleaseCharArrayElements(outputArray, outputChars, 0);

    return count;
}

static int latinime_BinaryDictionary_getBigrams(JNIEnv *env, jobject object, jint dict,
        jcharArray prevWordArray, jint prevWordLength, jintArray inputArray, jint inputArraySize,
        jcharArray outputArray, jintArray frequencyArray, jint maxWordLength, jint maxBigrams,
        jint maxAlternatives) {
    Dictionary *dictionary = (Dictionary*)dict;
    if (!dictionary) return 0;

    jchar *prevWord = env->GetCharArrayElements(prevWordArray, NULL);
    int *inputCodes = env->GetIntArrayElements(inputArray, NULL);
    jchar *outputChars = env->GetCharArrayElements(outputArray, NULL);
    int *frequencies = env->GetIntArrayElements(frequencyArray, NULL);

    int count = dictionary->getBigrams((unsigned short*) prevWord, prevWordLength, inputCodes,
            inputArraySize, (unsigned short*) outputChars, frequencies, maxWordLength, maxBigrams,
            maxAlternatives);

    env->ReleaseCharArrayElements(prevWordArray, prevWord, JNI_ABORT);
    env->ReleaseIntArrayElements(inputArray, inputCodes, JNI_ABORT);
    env->ReleaseCharArrayElements(outputArray, outputChars, 0);
    env->ReleaseIntArrayElements(frequencyArray, frequencies, 0);

    return count;
}

static jboolean latinime_BinaryDictionary_isValidWord(JNIEnv *env, jobject object, jint dict,
        jcharArray wordArray, jint wordLength) {
    Dictionary *dictionary = (Dictionary*)dict;
    if (!dictionary) return (jboolean) false;

    jchar *word = env->GetCharArrayElements(wordArray, NULL);
    jboolean result = dictionary->isValidWord((unsigned short*) word, wordLength);
    env->ReleaseCharArrayElements(wordArray, word, JNI_ABORT);

    return result;
}

static void latinime_BinaryDictionary_close(JNIEnv *env, jobject object, jint dict) {
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
        LOGE("DICT: Failure in munmap. ret=%d errno=%d", ret, errno);
    }
    ret = close(fd);
    if (ret != 0) {
        LOGE("DICT: Failure in close. ret=%d errno=%d", ret, errno);
    }
#else // USE_MMAP_FOR_DICTIONARY
    free(dictBuf);
#endif // USE_MMAP_FOR_DICTIONARY
}

static JNINativeMethod sMethods[] = {
    {"openNative", "(Ljava/lang/String;JJIIIII)I", (void*)latinime_BinaryDictionary_open},
    {"closeNative", "(I)V", (void*)latinime_BinaryDictionary_close},
    {"getSuggestionsNative", "(II[I[I[III[C[I)I", (void*)latinime_BinaryDictionary_getSuggestions},
    {"isValidWordNative", "(I[CI)Z", (void*)latinime_BinaryDictionary_isValidWord},
    {"getBigramsNative", "(I[CI[II[C[IIII)I", (void*)latinime_BinaryDictionary_getBigrams}
};

int register_BinaryDictionary(JNIEnv *env) {
    const char* const kClassPathName = "com/android/inputmethod/latin/BinaryDictionary";
    return registerNativeMethods(env, kClassPathName, sMethods,
            sizeof(sMethods) / sizeof(sMethods[0]));
}

} // namespace latinime
