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
 */

package org.apache.skywalking.oap.server.core.analysis.metrics;

import java.util.*;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataType;

/**
 * @author peng-yongsheng
 */
public class IntKeyLongValueHashMap extends HashMap<Integer, IntKeyLongValue> implements StorageDataType {

    public IntKeyLongValueHashMap() {
        super();
    }

    public IntKeyLongValueHashMap(int initialCapacity) {
        super(initialCapacity);
    }

    public IntKeyLongValueHashMap(String data) {
        super();
        toObject(data);
    }

    @Override public String toStorageData() {
        StringBuilder data = new StringBuilder();

        List<Map.Entry<Integer, IntKeyLongValue>> list = new ArrayList<>(this.entrySet());

        for (int i = 0; i < list.size(); i++) {
            if (i == 0) {
                data.append(list.get(i).getValue().toStorageData());
            } else {
                data.append(Const.ARRAY_SPLIT).append(list.get(i).getValue().toStorageData());
            }
        }
        return data.toString();
    }

    @Override public void toObject(String data) {
        String[] keyValues = data.split(Const.ARRAY_PARSER_SPLIT);
        for (String keyValue : keyValues) {
            IntKeyLongValue value = new IntKeyLongValue();
            value.toObject(keyValue);
            this.put(value.getKey(), value);
        }
    }

    @Override public void copyFrom(Object source) {
        IntKeyLongValueHashMap intKeyLongValueHashMap = (IntKeyLongValueHashMap)source;
        intKeyLongValueHashMap.values().forEach(value -> {
            IntKeyLongValue newValue = new IntKeyLongValue();
            newValue.copyFrom(value);
            this.put(newValue.getKey(), newValue);
        });
    }
}
