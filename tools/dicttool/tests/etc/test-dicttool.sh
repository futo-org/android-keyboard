#!/bin/bash
# Copyright 2012, The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

if [[ $(type -t mmm) != function ]]; then
echo "Usage:" 1>&2
echo "    source $0" 1>&2
echo "  or" 1>&2
echo "    . $0" 1>&2
if [[ ${BASH_SOURCE[0]} != $0 ]]; then return; else exit 1; fi
fi

find out -name "dicttool_aosp*" -exec rm -rf {} \; > /dev/null 2>&1
mmm -j8 external/junit
DICTTOOL_UNITTEST=true mmm -j8 packages/inputmethods/LatinIME/tools/dicttool
java -classpath ${ANDROID_HOST_OUT}/framework/junit-host.jar:${ANDROID_HOST_OUT}/framework/dicttool_aosp.jar junit.textui.TestRunner com.android.inputmethod.latin.makedict.BinaryDictEncoderFlattenTreeTests
java -classpath ${ANDROID_HOST_OUT}/framework/junit-host.jar:${ANDROID_HOST_OUT}/framework/dicttool_aosp.jar junit.textui.TestRunner com.android.inputmethod.latin.dicttool.BinaryDictOffdeviceUtilsTests
