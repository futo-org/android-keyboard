/*
 * Copyright (C) 2013 The Android Open Source Project
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

#ifndef LATINIME_LOG_UTILS_H
#define LATINIME_LOG_UTILS_H

#include <cstdio>
#include <stdarg.h>

#include "defines.h"
#include "jni.h"

namespace latinime {

class LogUtils {
 public:
    static void logToJava(JNIEnv *const env, const char *const format, ...)
#ifdef __GNUC__
            __attribute__ ((format (printf, 2, 3)))
#endif // __GNUC__
            {
        static const char *TAG = "LatinIME:LogUtils";
        const jclass androidUtilLogClass = env->FindClass("android/util/Log");
        const jmethodID logDotIMethodId = env->GetStaticMethodID(androidUtilLogClass, "i",
                "(Ljava/lang/String;Ljava/lang/String;)I");
        const jstring javaTag = env->NewStringUTF(TAG);

        va_list argList;
        va_start(argList, format);
        // Get the necessary size. Add 1 for the 0 terminator.
        const int size = vsnprintf(0, 0, format, argList) + 1;
        va_end(argList);
        char cString[size];
        va_start(argList, format);
        vsnprintf(cString, size, format, argList);
        va_end(argList);

        jstring javaString = env->NewStringUTF(cString);
        env->CallStaticIntMethod(androidUtilLogClass, logDotIMethodId, javaTag, javaString);
        env->DeleteLocalRef(javaString);
        env->DeleteLocalRef(javaTag);
        env->DeleteLocalRef(androidUtilLogClass);
    }

 private:
    DISALLOW_COPY_AND_ASSIGN(LogUtils);
};
} // namespace latinime
#endif // LATINIME_LOG_UTILS_H
