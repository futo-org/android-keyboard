//
// Created by hp on 11/22/23.
//

#ifndef LATINIME_JNI_UTILS_H
#define LATINIME_JNI_UTILS_H

#include <string>
#include "../jni_common.h"

std::string jstring2string(JNIEnv *env, jstring jStr);
jstring string2jstring(JNIEnv *env, const char *str);

#endif //LATINIME_JNI_UTILS_H
