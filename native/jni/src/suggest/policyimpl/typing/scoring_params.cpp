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

#include "suggest/policyimpl/typing/scoring_params.h"

namespace latinime {
// TODO: RENAME all
const float ScoringParams::MAX_SPATIAL_DISTANCE = 1.0f;
const int ScoringParams::THRESHOLD_NEXT_WORD_PROBABILITY = 40;
const int ScoringParams::THRESHOLD_NEXT_WORD_PROBABILITY_FOR_CAPPED = 120;
const float ScoringParams::AUTOCORRECT_OUTPUT_THRESHOLD = 1.0f;
const int ScoringParams::MAX_CACHE_DIC_NODE_SIZE = 125;
const int ScoringParams::THRESHOLD_SHORT_WORD_LENGTH = 4;

const float ScoringParams::DISTANCE_WEIGHT_LENGTH = 0.132f;
const float ScoringParams::PROXIMITY_COST = 0.086f;
const float ScoringParams::FIRST_PROXIMITY_COST = 0.104f;
const float ScoringParams::OMISSION_COST = 0.388f;
const float ScoringParams::OMISSION_COST_SAME_CHAR = 0.431f;
const float ScoringParams::OMISSION_COST_FIRST_CHAR = 0.532f;
const float ScoringParams::INSERTION_COST = 0.670f;
const float ScoringParams::INSERTION_COST_SAME_CHAR = 0.526f;
const float ScoringParams::INSERTION_COST_FIRST_CHAR = 0.563f;
const float ScoringParams::TRANSPOSITION_COST = 0.494f;
const float ScoringParams::SPACE_SUBSTITUTION_COST = 0.239f;
const float ScoringParams::ADDITIONAL_PROXIMITY_COST = 0.380f;
const float ScoringParams::SUBSTITUTION_COST = 0.363f;
const float ScoringParams::COST_NEW_WORD = 0.054f;
const float ScoringParams::COST_NEW_WORD_CAPITALIZED = 0.174f;
const float ScoringParams::DISTANCE_WEIGHT_LANGUAGE = 1.123f;
const float ScoringParams::COST_FIRST_LOOKAHEAD = 0.462f;
const float ScoringParams::COST_LOOKAHEAD = 0.092f;
const float ScoringParams::HAS_PROXIMITY_TERMINAL_COST = 0.126f;
const float ScoringParams::HAS_EDIT_CORRECTION_TERMINAL_COST = 0.056f;
const float ScoringParams::HAS_MULTI_WORD_TERMINAL_COST = 0.136f;
const float ScoringParams::TYPING_BASE_OUTPUT_SCORE = 1.0f;
const float ScoringParams::TYPING_MAX_OUTPUT_SCORE_PER_INPUT = 0.1f;
const float ScoringParams::MAX_NORM_DISTANCE_FOR_EDIT = 0.1f;
} // namespace latinime
