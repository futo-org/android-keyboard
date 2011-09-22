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

#ifndef LATINIME_DEFINES_TOUCH_POSITION_CORRECTION_H
#define LATINIME_DEFINES_TOUCH_POSITION_CORRECTION_H

#define OUTER_SWEET_SPOT_RADIUS_RATIO 1.3

static const char* TOUCH_POSITION_CORRECTION_GROUPS[] = {
        "qwertyuiop",
        "a",
        "sdfghjk",
        "l",
        "zxcvbnm",
};

// (center X) / (key width)
static const float RELATIVE_TOUCH_CENTER_X[] = {
        0,          // qwertyuiop
        -0.26871,   // a
        0,          // sdfghjk
        0.028050,   // l
        0,          // zxcvbnm
};

// (center Y) / (key height)
static const float RELATIVE_TOUCH_CENTER_Y[] = {
        0.192088,   // qwertyuiop
        0.214100,   // a
        0.216640,   // sdfghjk
        0.233288,   // l
        0.286598,   // zxcvbnm
};

// (sweet spot radius) / ((key width) + (key height))
static const float SWEET_SPOT_RADIUS[] = {
        0.148955,   // qwertyuiop
        0.185437,   // a
        0.145522,   // sdfghjk
        0.156882,   // l
        0.144211,   // zxcvbnm
};

#define CORRECTION_GROUP_COUNT \
    ((int)(sizeof(TOUCH_POSITION_CORRECTION_GROUPS) / sizeof(TOUCH_POSITION_CORRECTION_GROUPS[0])))

#endif // LATINIME_DEFINES_TOUCH_POSITION_CORRECTION_H
