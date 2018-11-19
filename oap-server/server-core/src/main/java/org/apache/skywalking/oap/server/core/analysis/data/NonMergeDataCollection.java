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

package org.apache.skywalking.oap.server.core.analysis.data;

import java.util.*;
import org.apache.skywalking.oap.server.core.storage.StorageData;

/**
 * @author peng-yongsheng
 */
public class NonMergeDataCollection<STORAGE_DATA extends StorageData> implements SWCollection<STORAGE_DATA> {

    private final List<STORAGE_DATA> data;
    private volatile boolean writing;
    private volatile boolean reading;

    NonMergeDataCollection() {
        this.data = new ArrayList<>();
        this.writing = false;
        this.reading = false;
    }

    public void finishWriting() {
        writing = false;
    }

    @Override public void writing() {
        writing = true;
    }

    @Override public boolean isWriting() {
        return writing;
    }

    @Override public void finishReading() {
        reading = false;
    }

    @Override public void reading() {
        reading = true;
    }

    @Override public boolean isReading() {
        return reading;
    }

    @Override public int size() {
        return data.size();
    }

    @Override public void clear() {
        data.clear();
    }

    @Override public boolean containsKey(STORAGE_DATA key) {
        throw new UnsupportedOperationException("Close merge data collection not support containsKey operation.");
    }

    @Override public STORAGE_DATA get(STORAGE_DATA key) {
        throw new UnsupportedOperationException("Close merge data collection not support get operation.");
    }

    @Override public void put(STORAGE_DATA value) {
        data.add(value);
    }

    @Override public Collection<STORAGE_DATA> collection() {
        return data;
    }
}
