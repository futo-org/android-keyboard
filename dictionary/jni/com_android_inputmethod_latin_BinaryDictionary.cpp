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

#define LOG_TAG "BinaryDictionary"
#include "utils/Log.h"

#include <stdio.h>
#include <assert.h>
#include <unistd.h>
#include <fcntl.h>

#include <nativehelper/jni.h>
#include "utils/AssetManager.h"
#include "utils/Asset.h"

#include "dictionary.h"

// ----------------------------------------------------------------------------

using namespace latinime;

using namespace android;

static jfieldID sDescriptorField;
static jfieldID sAssetManagerNativeField;
static jmethodID sAddWordMethod;

//
// helper function to throw an exception
//
static void throwException(JNIEnv *env, const char* ex, const char* fmt, int data)
{
    if (jclass cls = env->FindClass(ex)) {
        char msg[1000];
        sprintf(msg, fmt, data);
        env->ThrowNew(cls, msg);
        env->DeleteLocalRef(cls);
    }
}

static jint latinime_BinaryDictionary_open
        (JNIEnv *env, jobject object, jobject assetManager, jstring resourceString,
         jint typedLetterMultiplier, jint fullWordMultiplier)
{
    // Get the native file descriptor from the FileDescriptor object
    AssetManager *am = (AssetManager*) env->GetIntField(assetManager, sAssetManagerNativeField);
    if (!am) {
        LOGE("DICT: Couldn't get AssetManager native peer\n");
        return 0;
    }
    const char *resourcePath = env->GetStringUTFChars(resourceString, NULL);

    Asset *dictAsset = am->openNonAsset(resourcePath, Asset::ACCESS_BUFFER);
    if (dictAsset == NULL) {
        LOGE("DICT: Couldn't get asset %s\n", resourcePath);
        env->ReleaseStringUTFChars(resourceString, resourcePath);
        return 0;
    }

    void *dict = (void*) dictAsset->getBuffer(false);
    if (dict == NULL) {
        LOGE("DICT: Dictionary buffer is null\n");
        env->ReleaseStringUTFChars(resourceString, resourcePath);
        return 0;
    }
    Dictionary *dictionary = new Dictionary(dict, typedLetterMultiplier, fullWordMultiplier);
    dictionary->setAsset(dictAsset);

    env->ReleaseStringUTFChars(resourceString, resourcePath);
    return (jint) dictionary;
}

static int latinime_BinaryDictionary_getSuggestions(
        JNIEnv *env, jobject object, jint dict, jintArray inputArray, jint arraySize,
        jcharArray outputArray, jintArray frequencyArray, jint maxWordLength, jint maxWords,
        jint maxAlternatives, jint skipPos)
{
    Dictionary *dictionary = (Dictionary*) dict;
    if (dictionary == NULL)
        return 0;

    int *frequencies = env->GetIntArrayElements(frequencyArray, NULL);
    int *inputCodes = env->GetIntArrayElements(inputArray, NULL);
    jchar *outputChars = env->GetCharArrayElements(outputArray, NULL);

    int count = dictionary->getSuggestions(inputCodes, arraySize, (unsigned short*) outputChars, frequencies,
            maxWordLength, maxWords, maxAlternatives, skipPos);
    
    env->ReleaseIntArrayElements(frequencyArray, frequencies, 0);
    env->ReleaseIntArrayElements(inputArray, inputCodes, JNI_ABORT);
    env->ReleaseCharArrayElements(outputArray, outputChars, 0);
    
    return count;
}

static jboolean latinime_BinaryDictionary_isValidWord
        (JNIEnv *env, jobject object, jint dict, jcharArray wordArray, jint wordLength)
{
    Dictionary *dictionary = (Dictionary*) dict;
    if (dictionary == NULL) return (jboolean) false;

    jchar *word = env->GetCharArrayElements(wordArray, NULL);
    jboolean result = dictionary->isValidWord((unsigned short*) word, wordLength);
    env->ReleaseCharArrayElements(wordArray, word, JNI_ABORT);

    return result;
}

static void latinime_BinaryDictionary_close
        (JNIEnv *env, jobject object, jint dict)
{
    Dictionary *dictionary = (Dictionary*) dict;
    ((Asset*) dictionary->getAsset())->close();
    delete (Dictionary*) dict;
}

// ----------------------------------------------------------------------------

static JNINativeMethod gMethods[] = {
    {"openNative",           "(Landroid/content/res/AssetManager;Ljava/lang/String;II)I",
                                          (void*)latinime_BinaryDictionary_open},
    {"closeNative",          "(I)V",            (void*)latinime_BinaryDictionary_close},
    {"getSuggestionsNative", "(I[II[C[IIIII)I",  (void*)latinime_BinaryDictionary_getSuggestions},
    {"isValidWordNative",    "(I[CI)Z",         (void*)latinime_BinaryDictionary_isValidWord}
};

static int registerNativeMethods(JNIEnv* env, const char* className,
    JNINativeMethod* gMethods, int numMethods)
{
    jclass clazz;

    clazz = env->FindClass(className);
    if (clazz == NULL) {
        fprintf(stderr,
            "Native registration unable to find class '%s'\n", className);
        return JNI_FALSE;
    }
    if (env->RegisterNatives(clazz, gMethods, numMethods) < 0) {
        fprintf(stderr, "RegisterNatives failed for '%s'\n", className);
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

static int registerNatives(JNIEnv *env)
{
    const char* const kClassPathName = "com/android/inputmethod/latin/BinaryDictionary";
    jclass clazz;

    clazz = env->FindClass("java/io/FileDescriptor");
    if (clazz == NULL) {
        LOGE("Can't find %s", "java/io/FileDescriptor");
        return -1;
    }
    sDescriptorField = env->GetFieldID(clazz, "descriptor", "I");

    clazz = env->FindClass("android/content/res/AssetManager");
    if (clazz == NULL) {
        LOGE("Can't find %s", "java/io/FileDescriptor");
        return -1;
    }
    sAssetManagerNativeField = env->GetFieldID(clazz, "mObject", "I");

    return registerNativeMethods(env,
            kClassPathName, gMethods, sizeof(gMethods) / sizeof(gMethods[0]));
}

/*
 * Returns the JNI version on success, -1 on failure.
 */
jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    JNIEnv* env = NULL;
    jint result = -1;

    if (vm->GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK) {
        fprintf(stderr, "ERROR: GetEnv failed\n");
        goto bail;
    }
    assert(env != NULL);

    if (!registerNatives(env)) {
        fprintf(stderr, "ERROR: BinaryDictionary native registration failed\n");
        goto bail;
    }

    /* success -- return valid version number */
    result = JNI_VERSION_1_4;

bail:
    return result;
}
