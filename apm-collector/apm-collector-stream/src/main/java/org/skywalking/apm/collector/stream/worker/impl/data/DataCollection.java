/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
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
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.stream.worker.impl.data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.skywalking.apm.collector.core.cache.Collection;
import org.skywalking.apm.collector.core.data.Data;

/**
 * @author peng-yongsheng
 */
public class DataCollection implements Collection<Map<String, Data>> {
    private Map<String, Data> data;
    private volatile boolean writing;
    private volatile boolean reading;

    public DataCollection() {
        this.data = new ConcurrentHashMap<>();
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

    public boolean containsKey(String key) {
        return data.containsKey(key);
    }

    public void put(String key, Data value) {
        data.put(key, value);
    }

    public Data get(String key) {
        return data.get(key);
    }

    @Override public int size() {
        return data.size();
    }

    @Override public void clear() {
        data.clear();
    }

    public Map<String, Data> collection() {
        return data;
    }
}
