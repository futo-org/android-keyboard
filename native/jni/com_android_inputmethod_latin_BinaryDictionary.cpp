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

#define LOG_TAG "LatinIME: jni"

#include "dictionary.h"
#include "jni.h"

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

// ----------------------------------------------------------------------------

using namespace latinime;

//
// helper function to throw an exception
//
static void throwException(JNIEnv *env, const char* ex, const char* fmt, int data) {
    if (jclass cls = env->FindClass(ex)) {
        char msg[1000];
        sprintf(msg, fmt, data);
        env->ThrowNew(cls, msg);
        env->DeleteLocalRef(cls);
    }
}

static jint latinime_BinaryDictionary_open(JNIEnv *env, jobject object,
        jstring apkFileName, jlong dictOffset, jlong dictSize,
        jint typedLetterMultiplier, jint fullWordMultiplier, jint maxWordLength, jint maxWords,
        jint maxAlternatives) {
    PROF_OPEN;
    PROF_START(66);
    const char *apkFileNameChars = env->GetStringUTFChars(apkFileName, NULL);
    if (apkFileNameChars == NULL) {
        LOGE("DICT: Can't get apk file name");
        return 0;
    }
    int fd = 0;
    void *dictBuf = NULL;
    int adjust = 0;
#ifdef USE_MMAP_FOR_DICTIONARY
    /* mmap version */
    fd = open(apkFileNameChars, O_RDONLY);
    if (fd < 0) {
        LOGE("DICT: Can't open apk file. errno=%d", errno);
        return 0;
    }
    int pagesize = getpagesize();
    adjust = dictOffset % pagesize;
    int adjDictOffset = dictOffset - adjust;
    int adjDictSize = dictSize + adjust;
    dictBuf = mmap(NULL, sizeof(char) * adjDictSize, PROT_READ, MAP_PRIVATE, fd, adjDictOffset);
    if (dictBuf == MAP_FAILED) {
        LOGE("DICT: Can't mmap dictionary file. errno=%d", errno);
        return 0;
    }
    dictBuf = (void *)((char *)dictBuf + adjust);
#else // USE_MMAP_FOR_DICTIONARY
    /* malloc version */
    FILE *file = NULL;
    file = fopen(apkFileNameChars, "rb");
    if (file == NULL) {
        LOGE("DICT: Can't fopen apk file. errno=%d", errno);
        return 0;
    }
    dictBuf = malloc(sizeof(char) * dictSize);
    if (dictBuf == NULL) {
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
    env->ReleaseStringUTFChars(apkFileName, apkFileNameChars);

    if (!dictBuf) {
        LOGE("DICT: dictBuf is null");
        return 0;
    }
    Dictionary *dictionary = new Dictionary(dictBuf, dictSize, fd, adjust, typedLetterMultiplier,
            fullWordMultiplier, maxWordLength, maxWords, maxAlternatives);
    PROF_END(66);
    PROF_CLOSE;
    return (jint)dictionary;
}

static int latinime_BinaryDictionary_getSuggestions(JNIEnv *env, jobject object, jint dict,
        jintArray inputArray, jint arraySize, jcharArray outputArray, jintArray frequencyArray,
        jintArray nextLettersArray, jint nextLettersSize) {
    Dictionary *dictionary = (Dictionary*)dict;
    if (!dictionary) return 0;

    int *frequencies = env->GetIntArrayElements(frequencyArray, NULL);
    int *inputCodes = env->GetIntArrayElements(inputArray, NULL);
    jchar *outputChars = env->GetCharArrayElements(outputArray, NULL);
    int *nextLetters = nextLettersArray != NULL ? env->GetIntArrayElements(nextLettersArray, NULL)
            : NULL;

    int count = dictionary->getSuggestions(inputCodes, arraySize, (unsigned short*) outputChars,
            frequencies, nextLetters, nextLettersSize);

    env->ReleaseIntArrayElements(frequencyArray, frequencies, 0);
    env->ReleaseIntArrayElements(inputArray, inputCodes, JNI_ABORT);
    env->ReleaseCharArrayElements(outputArray, outputChars, 0);
    if (nextLetters) {
        env->ReleaseIntArrayElements(nextLettersArray, nextLetters, 0);
    }

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
    int ret = munmap((void *)((char *)dictBuf - dictionary->getDictBufAdjust()),
            dictionary->getDictSize() + dictionary->getDictBufAdjust());
    if (ret != 0) {
        LOGE("DICT: Failure in munmap. ret=%d errno=%d", ret, errno);
    }
    ret = close(dictionary->getMmapFd());
    if (ret != 0) {
        LOGE("DICT: Failure in close. ret=%d errno=%d", ret, errno);
    }
#else // USE_MMAP_FOR_DICTIONARY
    free(dictBuf);
#endif // USE_MMAP_FOR_DICTIONARY
    delete dictionary;
}

// ----------------------------------------------------------------------------

static JNINativeMethod gMethods[] = {
    {"openNative", "(Ljava/lang/String;JJIIIII)I", (void*)latinime_BinaryDictionary_open},
    {"closeNative", "(I)V", (void*)latinime_BinaryDictionary_close},
    {"getSuggestionsNative", "(I[II[C[I[II)I", (void*)latinime_BinaryDictionary_getSuggestions},
    {"isValidWordNative", "(I[CI)Z", (void*)latinime_BinaryDictionary_isValidWord},
    {"getBigramsNative", "(I[CI[II[C[IIII)I", (void*)latinime_BinaryDictionary_getBigrams}
};

static int registerNativeMethods(JNIEnv* env, const char* className, JNINativeMethod* gMethods,
        int numMethods) {
    jclass clazz;

    clazz = env->FindClass(className);
    if (clazz == NULL) {
        LOGE("Native registration unable to find class '%s'", className);
        return JNI_FALSE;
    }
    if (env->RegisterNatives(clazz, gMethods, numMethods) < 0) {
        LOGE("RegisterNatives failed for '%s'", className);
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

static int registerNatives(JNIEnv *env) {
    const char* const kClassPathName = "com/android/inputmethod/latin/BinaryDictionary";
    return registerNativeMethods(env, kClassPathName, gMethods,
            sizeof(gMethods) / sizeof(gMethods[0]));
}

/*
 * Returns the JNI version on success, -1 on failure.
 */
jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env = NULL;
    jint result = -1;

    if (vm->GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK) {
        LOGE("ERROR: GetEnv failed");
        goto bail;
    }
    assert(env != NULL);

    if (!registerNatives(env)) {
        LOGE("ERROR: BinaryDictionary native registration failed");
        goto bail;
    }

    /* success -- return valid version number */
    result = JNI_VERSION_1_4;

bail:
    return result;
}
