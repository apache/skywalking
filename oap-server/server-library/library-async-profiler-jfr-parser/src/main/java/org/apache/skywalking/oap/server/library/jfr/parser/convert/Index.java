/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

/*
 * Copyright The async-profiler authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.skywalking.oap.server.library.jfr.parser.convert;

import java.lang.reflect.Array;
import java.util.HashMap;

public class Index<T> extends HashMap<T, Integer> {
    private final Class<T> cls;

    public Index(Class<T> cls, T empty) {
        this.cls = cls;
        super.put(empty, 0);
    }

    public int index(T key) {
        Integer index = super.get(key);
        if (index != null) {
            return index;
        } else {
            int newIndex = super.size();
            super.put(key, newIndex);
            return newIndex;
        }
    }

    @SuppressWarnings("unchecked")
    public T[] keys() {
        T[] result = (T[]) Array.newInstance(cls, size());
        for (Entry<T, Integer> entry : entrySet()) {
            result[entry.getValue()] = entry.getKey();
        }
        return result;
    }
}
