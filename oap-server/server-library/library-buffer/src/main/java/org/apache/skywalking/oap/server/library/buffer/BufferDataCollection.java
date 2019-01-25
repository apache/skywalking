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

package org.apache.skywalking.oap.server.library.buffer;

import com.google.protobuf.GeneratedMessageV3;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author peng-yongsheng
 */
public class BufferDataCollection<MESSAGE_TYPE extends GeneratedMessageV3> {

    private AtomicInteger index = new AtomicInteger(0);
    private final List<BufferData<MESSAGE_TYPE>> bufferDataList;

    public BufferDataCollection(int size) {
        this.bufferDataList = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            bufferDataList.add(null);
        }
    }

    public void add(BufferData<MESSAGE_TYPE> bufferData) {
        bufferDataList.set(index.getAndIncrement(), bufferData);

    }

    public int size() {
        return index.get();
    }

    public synchronized List<BufferData<MESSAGE_TYPE>> export() {
        List<BufferData<MESSAGE_TYPE>> exportData = new ArrayList<>(index.get());
        for (int i = 0; i < index.get(); i++) {
            exportData.add(bufferDataList.get(i));
        }
        index.set(0);
        return exportData;
    }
}
