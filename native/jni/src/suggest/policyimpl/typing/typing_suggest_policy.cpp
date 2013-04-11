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

#include "suggest.h"
#include "typing_suggest.h"
#include "typing_suggest_policy.h"

namespace latinime {

const TypingSuggestPolicy TypingSuggestPolicy::sInstance;

// A factory method for a "typing" Suggest instance
static SuggestInterface *getTypingSuggestInstance() {
    return new Suggest(TypingSuggestPolicy::getInstance());
}

// An ad-hoc internal class to register the factory method getTypingSuggestInstance() defined above
class TypingSuggestFactoryRegisterer {
 public:
    TypingSuggestFactoryRegisterer() {
        TypingSuggest::setTypingSuggestFactoryMethod(getTypingSuggestInstance);
    }
 private:
    DISALLOW_COPY_AND_ASSIGN(TypingSuggestFactoryRegisterer);
};

// To invoke the TypingSuggestFactoryRegisterer's constructor in the global constructor
static TypingSuggestFactoryRegisterer typingSuggestFactoryregisterer;
} // namespace latinime
