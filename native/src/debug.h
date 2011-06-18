/*
**
** Copyright 2011, The Android Open Source Project
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

#ifndef LATINIME_DEBUG_H
#define LATINIME_DEBUG_H

#include "defines.h"

static inline unsigned char* convertToUnibyteString(unsigned short* input, unsigned char* output,
        const unsigned int length) {
    int i = 0;
    for (; i <= length && input[i] != 0; ++i)
        output[i] = input[i] & 0xFF;
    output[i] = 0;
    return output;
}

static inline unsigned char* convertToUnibyteStringAndReplaceLastChar(unsigned short* input,
        unsigned char* output, const unsigned int length, unsigned char c) {
    int i = 0;
    for (; i <= length && input[i] != 0; ++i)
        output[i] = input[i] & 0xFF;
    output[i-1] = c;
    output[i] = 0;
    return output;
}

static inline void LOGI_S16(unsigned short* string, const unsigned int length) {
    unsigned char tmp_buffer[length];
    convertToUnibyteString(string, tmp_buffer, length);
    LOGI(">> %s", tmp_buffer);
    // The log facility is throwing out log that comes too fast. The following
    // is a dirty way of slowing down processing so that we can see all log.
    // TODO : refactor this in a blocking log or something.
    // usleep(10);
}

static inline void LOGI_S16_PLUS(unsigned short* string, const unsigned int length,
        unsigned char c) {
    unsigned char tmp_buffer[length+1];
    convertToUnibyteStringAndReplaceLastChar(string, tmp_buffer, length, c);
    LOGI(">> %s", tmp_buffer);
    // Likewise
    // usleep(10);
}

static inline void printDebug(const char* tag, int* codes, int codesSize, int MAX_PROXIMITY_CHARS) {
    unsigned char *buf = (unsigned char*)malloc((1 + codesSize) * sizeof(*buf));

    buf[codesSize] = 0;
    while (--codesSize >= 0)
        buf[codesSize] = (unsigned char)codes[codesSize * MAX_PROXIMITY_CHARS];
    LOGI("%s, WORD = %s", tag, buf);

    free(buf);
}

#endif // LATINIME_DEBUG_H
