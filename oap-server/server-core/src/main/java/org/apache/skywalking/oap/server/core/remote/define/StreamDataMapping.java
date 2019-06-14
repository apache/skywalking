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
    private List<Class<? extends StreamData>> streamClassList;
    private final Map<Class<? extends StreamData>, Integer> classMap;
    private final Map<Integer, Class<? extends StreamData>> idMap;

    public StreamDataMapping() {
        streamClassList = new ArrayList<>();
        this.classMap = new HashMap<>();
        this.idMap = new HashMap<>();
    }

    @Override public synchronized void putIfAbsent(Class<? extends StreamData> streamDataClass) {
        if (classMap.containsKey(streamDataClass)) {
            return;
        }

        streamClassList.add(streamDataClass);
    }

    public void init() {
        /**
         * The stream protocol use this list order to assign the ID,
         * which is used in across node communication. This order must be certain.
         */
        Collections.sort(streamClassList, new Comparator<Class>() {
            @Override public int compare(Class streamClass1, Class streamClass2) {
                return streamClass1.getName().compareTo(streamClass2.getName());
            }
        });

        for (int i = 0; i < streamClassList.size(); i++) {
            Class<? extends StreamData> streamClass = streamClassList.get(i);
            int streamId = i + 1;
            classMap.put(streamClass, streamId);
            idMap.put(streamId, streamClass);
        }
    }

    @Override public int findIdByClass(Class<? extends StreamData> streamDataClass) {
        return classMap.get(streamDataClass);
    }

    @Override public Class<? extends StreamData> findClassById(int id) {
        return idMap.get(id);
    }
}
