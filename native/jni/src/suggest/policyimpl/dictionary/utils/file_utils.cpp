/*
 * Copyright (C) 2013, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "suggest/policyimpl/dictionary/utils/file_utils.h"

#include <cstdio>
#include <cstring>
#include <dirent.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>

namespace latinime {

// Returns -1 on error.
/* static */ int FileUtils::getFileSize(const char *const filePath) {
    const int fd = open(filePath, O_RDONLY);
    if (fd == -1) {
        return -1;
    }
    struct stat statBuf;
    if (fstat(fd, &statBuf) != 0) {
        close(fd);
        return -1;
    }
    close(fd);
    return static_cast<int>(statBuf.st_size);
}

// Remove a directory and all files in the directory.
/* static */ bool FileUtils::removeDirAndFiles(const char *const dirPath) {
    DIR *const dir = opendir(dirPath);
    if (dir == NULL) {
        AKLOGE("Cannot open dir %s.", dirPath);
        return true;
    }
    struct dirent *dirent;
    while ((dirent = readdir(dir)) != NULL) {
        if (dirent->d_type != DT_REG) {
            continue;
        }
        const int filePathBufSize = getFilePathBufSize(dirPath, dirent->d_name);
        char filePath[filePathBufSize];
        getFilePath(dirPath, dirent->d_name, filePathBufSize, filePath);
        if (remove(filePath) != 0) {
            AKLOGE("Cannot remove file %s.", filePath);
            return false;
        }
    }
    if (remove(dirPath) != 0) {
        AKLOGE("Cannot remove directory %s.", dirPath);
        return false;
    }
    return true;
}

/* static */ int FileUtils::getFilePathWithSuffixBufSize(const char *const filePath,
        const char *const suffix) {
    return strlen(filePath) + strlen(suffix) + 1 /* terminator */;
}

/* static */ void FileUtils::getFilePathWithSuffix(const char *const filePath,
        const char *const suffix, const int filePathBufSize, char *const outFilePath) {
    snprintf(outFilePath, filePathBufSize, "%s%s", filePath, suffix);
}

/* static */ int FileUtils::getFilePathBufSize(const char *const dirPath,
        const char *const fileName) {
    return strlen(dirPath) + 1 /* '/' */ + strlen(fileName) + 1 /* terminator */;
}

/* static */ void FileUtils::getFilePath(const char *const dirPath, const char *const fileName,
        const int filePathBufSize, char *const outFilePath) {
    snprintf(outFilePath, filePathBufSize, "%s/%s", dirPath, fileName);
}

/* static */ bool FileUtils::getFilePathWithoutSuffix(const char *const filePath,
        const char *const suffix, const int outDirPathBufSize, char *const outDirPath) {
    const int filePathLength = strlen(filePath);
    const int suffixLength = strlen(suffix);
    if (filePathLength <= suffixLength) {
        AKLOGE("File path length (%s:%d) is shorter that suffix length (%s:%d).",
                filePath, filePathLength, suffix, suffixLength);
        return false;
    }
    const int resultFilePathLength = filePathLength - suffixLength;
    if (outDirPathBufSize <= resultFilePathLength) {
        AKLOGE("outDirPathBufSize is too small. filePath: %s, suffix: %s, outDirPathBufSize: %d",
                filePath, suffix, outDirPathBufSize);
        return false;
    }
    if (strncmp(filePath + resultFilePathLength, suffix, suffixLength) != 0) {
        AKLOGE("File Path %s does not have %s as a suffix", filePath, suffix);
        return false;
    }
    snprintf(outDirPath, resultFilePathLength + 1 /* terminator */, "%s", filePath);
    return true;
}

/* static */ void FileUtils::getDirPath(const char *const filePath, const int outDirPathBufSize,
        char *const outDirPath) {
    for (int i = strlen(filePath) - 1; i >= 0; --i) {
        if (filePath[i] == '/') {
            if (i >= outDirPathBufSize) {
                AKLOGE("outDirPathBufSize is too small. filePath: %s, outDirPathBufSize: %d",
                        filePath, outDirPathBufSize);
                ASSERT(false);
                return;
            }
            snprintf(outDirPath, i + 1 /* terminator */, "%s", filePath);
            return;
        }
    }
}

} // namespace latinime
