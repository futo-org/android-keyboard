#!/bin/bash
# Copyright 2014, The Android Open Source Project
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

# check script arguments
if [[ $(type -t mmm) != function ]]; then
if [[ ${BASH_SOURCE[0]} != $0 ]]; then return; else exit 1; fi
fi

# Host build is never supported in unbundled (NDK/tapas) build
if [[ -n $TARGET_BUILD_APPS ]]; then
  echo "Host build is never supported in tapas build."  1>&2
  echo "Use lunch command instead."  1>&2
  if [[ ${BASH_SOURCE[0]} != $0 ]]; then return; else exit 1; fi
fi

test_name=dicttoolkit_unittests

pushd $PWD > /dev/null
cd $(gettop)
(mmm -j16 packages/inputmethods/LatinIME/native/dicttoolkit) || (make -j16 $test_name)
$ANDROID_HOST_OUT/bin/$test_name
popd > /dev/null
