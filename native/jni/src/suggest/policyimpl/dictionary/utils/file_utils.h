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

#ifndef LATINIME_FILE_UTILS_H
#define LATINIME_FILE_UTILS_H

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>

#include "defines.h"

namespace latinime {

class FileUtils {
 public:
    // Returns -1 on error.
    static int getFileSize(const char *const filePath) {
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

 private:
    DISALLOW_IMPLICIT_CONSTRUCTORS(FileUtils);
};
} // namespace latinime
#endif /* LATINIME_FILE_UTILS_H */
