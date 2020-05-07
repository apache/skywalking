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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.oap.server.core.storage.ComparableStorageData;
import org.apache.skywalking.oap.server.core.storage.StorageData;

/**
 * LimitedSizeBufferedData is a thread no safe implementation of {@link BufferedData}. It collects limited records of
 * each {@link StorageData#id()}.
 */
public class LimitedSizeBufferedData<STORAGE_DATA extends ComparableStorageData & StorageData> implements BufferedData<STORAGE_DATA> {
    private final HashMap<String, LinkedList<STORAGE_DATA>> data;
    private final int limitedSize;

    public LimitedSizeBufferedData(int limitedSize) {
        this.data = new HashMap<>();
        this.limitedSize = limitedSize;
    }

    @Override
    public void accept(final STORAGE_DATA data) {
        final String id = data.id();
        LinkedList<STORAGE_DATA> storageDataList = this.data.get(id);
        if (storageDataList == null) {
            storageDataList = new LinkedList<>();
            this.data.put(id, storageDataList);
        }

        if (storageDataList.size() < limitedSize) {
            storageDataList.add(data);
            return;
        }

        for (int i = 0; i < storageDataList.size(); i++) {
            STORAGE_DATA storageData = storageDataList.get(i);
            if (data.compareTo(storageData) <= 0) {
                if (i == 0) {
                    // input data is less than the smallest in top N list, ignore
                } else {
                    // Remove the smallest in top N list
                    // add the current data into the right position
                    storageDataList.add(i, data);
                    storageDataList.removeFirst();
                }
                return;
            }
        }

        // Add the data as biggest in top N list
        storageDataList.addLast(data);
        storageDataList.removeFirst();
    }

    @Override
    public List<STORAGE_DATA> read() {
        try {
            List<STORAGE_DATA> collection = new ArrayList<>();
            data.values().forEach(collection::addAll);
            return collection;
        } finally {
            data.clear();
        }
    }
}
