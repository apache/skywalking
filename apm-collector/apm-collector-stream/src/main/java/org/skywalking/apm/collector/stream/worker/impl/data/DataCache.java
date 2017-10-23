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

import org.skywalking.apm.collector.core.stream.Data;

/**
 * @author peng-yongsheng
 */
public class DataCache extends Window {

    private DataCollection lockedDataCollection;

    public boolean containsKey(String id) {
        return lockedDataCollection.containsKey(id);
    }

    public Data get(String id) {
        return lockedDataCollection.get(id);
    }

    public void put(String id, Data data) {
        lockedDataCollection.put(id, data);
    }

    public void writing() {
        lockedDataCollection = getCurrentAndWriting();
    }

    public int currentCollectionSize() {
        return getCurrent().size();
    }

    public void finishWriting() {
        lockedDataCollection.finishWriting();
        lockedDataCollection = null;
    }
}
