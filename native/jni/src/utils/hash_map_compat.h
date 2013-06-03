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

// TODO: Use std::unordered_map that has been standardized in C++11

#ifdef __APPLE__
#include <ext/hash_map>
#else // __APPLE__
#include <hash_map>
#endif // __APPLE__

#ifdef __SGI_STL_PORT
#define hash_map_compat stlport::hash_map
#else // __SGI_STL_PORT
#define hash_map_compat __gnu_cxx::hash_map
#endif // __SGI_STL_PORT

#endif // LATINIME_HASH_MAP_COMPAT_H
