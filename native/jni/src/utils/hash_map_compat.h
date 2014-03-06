/*
 * Copyright (C) 2012 The Android Open Source Project
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

#ifndef LATINIME_HASH_MAP_COMPAT_H
#define LATINIME_HASH_MAP_COMPAT_H

#include <unordered_map>

#define hash_map_compat std::unordered_map

#if 0 // TODO: Use this instead of the above macro.
template <typename TKey, typename TValue> using hash_map_compat = std::unordered_map<TKey, TValue>;
#endif

#endif // LATINIME_HASH_MAP_COMPAT_H
