//
// Created by hp on 11/22/23.
//

#include "jni_utils.h"
#include <string>
#include "defines.h"

std::string jstring2string(JNIEnv *env, jstring jStr) {
    const jsize stringUtf8Length = env->GetStringUTFLength(jStr);
    if (stringUtf8Length <= 0) {
        AKLOGE("Can't get jStr");
        return "";
    }
    char stringChars[stringUtf8Length + 1];
    env->GetStringUTFRegion(jStr, 0, env->GetStringLength(jStr), stringChars);
    stringChars[stringUtf8Length] = '\0';

    return {stringChars};
}