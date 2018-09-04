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

package org.apache.skywalking.oap.server.core.remote.annotation;

import java.util.*;
import org.apache.skywalking.oap.server.core.remote.data.StreamData;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class StreamDataAnnotationContainer implements StreamDataClassGetter {

    private static final Logger logger = LoggerFactory.getLogger(StreamDataAnnotationContainer.class);

    private int id = 0;
    private final Map<Class<StreamData>, Integer> classMap;
    private final Map<Integer, Class<StreamData>> idMap;

    public StreamDataAnnotationContainer() {
        this.classMap = new HashMap<>();
        this.idMap = new HashMap<>();
    }

    @SuppressWarnings(value = "unchecked")
    public synchronized void generate(List<Class> streamDataClasses) {
        streamDataClasses.sort(Comparator.comparing(Class::getName));

        for (Class streamDataClass : streamDataClasses) {
            id++;
            classMap.put(streamDataClass, id);
            idMap.put(id, streamDataClass);
        }
    }

    @Override public int findIdByClass(Class streamDataClass) {
        return classMap.get(streamDataClass);
    }

    @Override public Class<StreamData> findClassById(int id) {
        return idMap.get(id);
    }
}
