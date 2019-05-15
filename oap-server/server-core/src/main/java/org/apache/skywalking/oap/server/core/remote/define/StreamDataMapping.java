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

package org.apache.skywalking.oap.server.core.remote.define;

import java.util.*;
import org.apache.skywalking.oap.server.core.remote.data.StreamData;

/**
 * @author peng-yongsheng
 */
public class StreamDataMapping implements StreamDataMappingGetter, StreamDataMappingSetter {

    private int id = 0;
    private final Map<Class<? extends StreamData>, Integer> classMap;
    private final Map<Integer, Class<? extends StreamData>> idMap;

    public StreamDataMapping() {
        this.classMap = new HashMap<>();
        this.idMap = new HashMap<>();
    }

    @Override public synchronized void putIfAbsent(Class<? extends StreamData> streamDataClass) {
        if (classMap.containsKey(streamDataClass)) {
            return;
        }

        id++;
        classMap.put(streamDataClass, id);
        idMap.put(id, streamDataClass);
    }

    @Override public int findIdByClass(Class<? extends StreamData> streamDataClass) {
        return classMap.get(streamDataClass);
    }

    @Override public Class<? extends StreamData> findClassById(int id) {
        return idMap.get(id);
    }
}
