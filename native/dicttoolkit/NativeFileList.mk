# Copyright (C) 2014 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LATIN_IME_DICT_TOOLKIT_MAIN_SRC_FILES := \
    dict_toolkit_main.cpp

LATIN_IME_DICT_TOOLKIT_SRC_FILES := \
    $(addprefix command_executors/, \
        diff_executor.cpp \
        header_executor.cpp \
        help_executor.cpp \
        info_executor.cpp \
        makedict_executor.cpp) \
    $(addprefix offdevice_intermediate_dict/, \
        offdevice_intermediate_dict.cpp) \
    $(addprefix utils/, \
        arguments_parser.cpp \
        command_utils.cpp \
        utf8_utils.cpp)

LATIN_IME_DICT_TOOLKIT_TEST_FILES := \
    $(addprefix command_executors/, \
        diff_executor_test.cpp \
        header_executor_test.cpp \
        info_executor_test.cpp \
        makedict_executor_test.cpp) \
    dict_toolkit_defines_test.cpp \
    $(addprefix offdevice_intermediate_dict/, \
        offdevice_intermediate_dict_test.cpp) \
    $(addprefix utils/, \
        arguments_parser_test.cpp \
        command_utils_test.cpp \
        utf8_utils_test.cpp)
