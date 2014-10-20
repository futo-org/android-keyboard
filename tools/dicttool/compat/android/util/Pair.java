/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.util;

import java.util.Objects;

public class Pair<T1, T2> {
    public final T1 mFirst;
    public final T2 mSecond;

    public Pair(final T1 first, final T2 second) {
        mFirst = first;
        mSecond = second;
    }

    @Override
    public int hashCode() {
        return (mFirst == null ? 0 : mFirst.hashCode())
                ^ (mSecond == null ? 0 : mSecond.hashCode());
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof Pair)) return false;
        Pair<?, ?> p = (Pair<?, ?>)o;
        return Objects.equals(mFirst, p.mFirst) && Objects.equals(mSecond, p.mSecond);
    }
}
