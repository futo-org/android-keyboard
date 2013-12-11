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

#include "suggest/policyimpl/dictionary/structure/v4/ver4_dict_buffers.h"

#include <cerrno>
#include <sys/stat.h>
#include <sys/types.h>

#include "suggest/policyimpl/dictionary/utils/dict_file_writing_utils.h"
#include "suggest/policyimpl/dictionary/utils/file_utils.h"

namespace latinime {

/* static */ Ver4DictBuffers::Ver4DictBuffersPtr Ver4DictBuffers::openVer4DictBuffers(
        const char *const dictDirPath, const MmappedBuffer::MmappedBufferPtr &headerBuffer) {
    const bool isUpdatable = headerBuffer.get() ? headerBuffer.get()->isUpdatable() : false;
    // TODO: take only dictDirPath, and open both header and trie files in the constructor below
    return Ver4DictBuffersPtr(new Ver4DictBuffers(dictDirPath, headerBuffer, isUpdatable));
}

bool Ver4DictBuffers::flushHeaderAndDictBuffers(const char *const dictDirPath,
        const BufferWithExtendableBuffer *const headerBuffer) const {
    // Create temporary directory.
    const int tmpDirPathBufSize = FileUtils::getFilePathWithSuffixBufSize(dictDirPath,
            DictFileWritingUtils::TEMP_FILE_SUFFIX_FOR_WRITING_DICT_FILE);
    char tmpDirPath[tmpDirPathBufSize];
    FileUtils::getFilePathWithSuffix(dictDirPath,
            DictFileWritingUtils::TEMP_FILE_SUFFIX_FOR_WRITING_DICT_FILE, tmpDirPathBufSize,
            tmpDirPath);
    if (FileUtils::existsDir(tmpDirPath)) {
        if (!FileUtils::removeDirAndFiles(tmpDirPath)) {
            AKLOGE("Existing directory %s cannot be removed.", tmpDirPath);
            ASSERT(false);
            return false;
        }
    }
    if (mkdir(tmpDirPath, S_IRWXU) == -1) {
        AKLOGE("Cannot create directory: %s. errno: %d.", tmpDirPath, errno);
        return false;
    }
    // Write header file.
    if (!DictFileWritingUtils::flushBufferToFileInDir(tmpDirPath,
            Ver4DictConstants::HEADER_FILE_EXTENSION, headerBuffer)) {
        AKLOGE("Dictionary header file %s/%s cannot be written.", tmpDirPath,
                Ver4DictConstants::HEADER_FILE_EXTENSION);
        return false;
    }
    // Write trie file.
    if (!DictFileWritingUtils::flushBufferToFileInDir(tmpDirPath,
            Ver4DictConstants::TRIE_FILE_EXTENSION, &mExpandableTrieBuffer)) {
        AKLOGE("Dictionary trie file %s/%s cannot be written.", tmpDirPath,
                Ver4DictConstants::TRIE_FILE_EXTENSION);
        return false;
    }
    // Write dictionary contents.
    if (!mTerminalPositionLookupTable.flushToFile(tmpDirPath)) {
        AKLOGE("Terminal position lookup table cannot be written. %s", tmpDirPath);
        return false;
    }
    if (!mProbabilityDictContent.flushToFile(tmpDirPath)) {
        AKLOGE("Probability dict content cannot be written. %s", tmpDirPath);
        return false;
    }
    if (!mBigramDictContent.flushToFile(tmpDirPath)) {
        AKLOGE("Bigram dict content cannot be written. %s", tmpDirPath);
        return false;
    }
    if (!mShortcutDictContent.flushToFile(tmpDirPath)) {
        AKLOGE("Shortcut dict content cannot be written. %s", tmpDirPath);
        return false;
    }
    // Remove existing dictionary.
    if (!FileUtils::removeDirAndFiles(dictDirPath)) {
        AKLOGE("Existing directory %s cannot be removed.", dictDirPath);
        ASSERT(false);
        return false;
    }
    // Rename temporary directory.
    if (rename(tmpDirPath, dictDirPath) != 0) {
        AKLOGE("%s cannot be renamed to %s", tmpDirPath, dictDirPath);
        ASSERT(false);
        return false;
    }
    return true;
}

Ver4DictBuffers::Ver4DictBuffers(const char *const dictDirPath,
        const MmappedBuffer::MmappedBufferPtr &headerBuffer, const bool isUpdatable)
        : mHeaderBuffer(headerBuffer),
          mDictBuffer(MmappedBuffer::openBuffer(dictDirPath,
                  Ver4DictConstants::TRIE_FILE_EXTENSION, isUpdatable)),
          mHeaderPolicy(headerBuffer.get()->getBuffer(), FormatUtils::VERSION_4),
          mExpandableHeaderBuffer(headerBuffer.get()->getBuffer(), mHeaderPolicy.getSize(),
                  BufferWithExtendableBuffer::DEFAULT_MAX_ADDITIONAL_BUFFER_SIZE),
          mExpandableTrieBuffer(mDictBuffer.get()->getBuffer(),
                  mDictBuffer.get()->getBufferSize(),
                  BufferWithExtendableBuffer::DEFAULT_MAX_ADDITIONAL_BUFFER_SIZE),
          mTerminalPositionLookupTable(dictDirPath, isUpdatable),
          mProbabilityDictContent(dictDirPath, mHeaderPolicy.hasHistoricalInfoOfWords(),
                  isUpdatable),
          mBigramDictContent(dictDirPath, mHeaderPolicy.hasHistoricalInfoOfWords(),
                  isUpdatable),
          mShortcutDictContent(dictDirPath, isUpdatable),
          mIsUpdatable(isUpdatable) {}

Ver4DictBuffers::Ver4DictBuffers(const HeaderPolicy *const headerPolicy)
        : mHeaderBuffer(0), mDictBuffer(0), mHeaderPolicy(),
          mExpandableHeaderBuffer(Ver4DictConstants::MAX_DICTIONARY_SIZE),
          mExpandableTrieBuffer(Ver4DictConstants::MAX_DICTIONARY_SIZE),
          mTerminalPositionLookupTable(),
          mProbabilityDictContent(headerPolicy->hasHistoricalInfoOfWords()),
          mBigramDictContent(headerPolicy->hasHistoricalInfoOfWords()), mShortcutDictContent(),
          mIsUpdatable(true) {}

} // namespace latinime
