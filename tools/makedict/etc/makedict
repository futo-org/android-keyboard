#!/bin/sh
# Copyright 2009, The Android Open Source Project
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

# Set up prog to be the path of this script, including following symlinks,
# and set up progdir to be the fully-qualified pathname of its directory.
prog="$0"
while [ -h "${prog}" ]; do
    newProg=`/bin/ls -ld "${prog}"`
    newProg=`expr "${newProg}" : ".* -> \(.*\)$"`
    if expr "x${newProg}" : 'x/' >/dev/null; then
        prog="${newProg}"
    else
        progdir=`dirname "${prog}"`
        prog="${progdir}/${newProg}"
    fi
done
oldwd=`pwd`
progdir=`dirname "${prog}"`
cd "${progdir}"
progdir=`pwd`
prog="${progdir}"/`basename "${prog}"`
cd "${oldwd}"

jarfile=makedict.jar
frameworkdir="$progdir"
if [ ! -r "$frameworkdir/$jarfile" ]
then
    frameworkdir=`dirname "$progdir"`/tools/lib
    libdir=`dirname "$progdir"`/tools/lib
fi
if [ ! -r "$frameworkdir/$jarfile" ]
then
    frameworkdir=`dirname "$progdir"`/framework
    libdir=`dirname "$progdir"`/lib
fi
if [ ! -r "$frameworkdir/$jarfile" ]
then
    echo `basename "$prog"`": can't find $jarfile"
    exit 1
fi

if [ "$OSTYPE" = "cygwin" ] ; then
    jarpath=`cygpath -w  "$frameworkdir/$jarfile"`
    progdir=`cygpath -w  "$progdir"`
else
    jarpath="$frameworkdir/$jarfile"
fi

# need to use "java.ext.dirs" because "-jar" causes classpath to be ignored
# might need more memory, e.g. -Xmx128M
exec java -Djava.ext.dirs="$frameworkdir" -jar "$jarpath" "$@"
