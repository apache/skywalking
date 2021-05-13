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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataComplexObject;

/**
 * DataTable includes a hashmap to store string key and long value. It enhanced the serialization capability.
 */
@ToString
@EqualsAndHashCode
public class DataTable implements StorageDataComplexObject<DataTable> {
    private HashMap<String, Long> data;

    public DataTable() {
        data = new HashMap<>();
    }

    public DataTable(int initialCapacity) {
        data = new HashMap<>(initialCapacity);
    }

    public DataTable(String data) {
        this();
        toObject(data);
    }

    public Long get(String key) {
        return data.get(key);
    }

    public void put(String key, Long value) {
        data.put(key, value);
    }

    /**
     * Accumulate the value with existing value in the same given key.
     */
    public void valueAccumulation(String key, Long value) {
        Long element = data.get(key);
        if (element == null) {
            element = value;
        } else {
            element += value;
        }
        data.put(key, element);
    }

    /**
     * @return the sum of all values.
     */
    public long sumOfValues() {
        return data.values().stream().mapToLong(element -> element).sum();
    }

    public boolean keysEqual(DataTable that) {
        if (this.data.keySet().size() != that.data.keySet().size()) {
            return false;
        }
        return this.data.keySet().equals(that.data.keySet());
    }

    public List<String> sortedKeys(Comparator<String> keyComparator) {
        return data.keySet().stream().sorted(keyComparator).collect(Collectors.toList());
    }

    public List<Long> sortedValues(Comparator<String> keyComparator) {
        final List<String> collect = data.keySet().stream().sorted(keyComparator).collect(Collectors.toList());
        List<Long> values = new ArrayList<>(collect.size());
        collect.forEach(key -> values.add(data.get(key)));
        return values;
    }

    public Set<String> keys() {
        return data.keySet();
    }

    public boolean hasData() {
        return !data.isEmpty();
    }

    public boolean hasKey(String key) {
        return data.containsKey(key);
    }

    public int size() {
        return data.size();
    }

    @Override
    public String toStorageData() {
        StringBuilder builder = new StringBuilder();

        this.data.forEach((key, value) -> {
            if (builder.length() != 0) {
                // For the first element.
                builder.append(Const.ARRAY_SPLIT);
            }
            builder.append(key).append(Const.KEY_VALUE_SPLIT).append(value);
        });
        return builder.toString();
    }

    @Override
    public void toObject(String data) {
        String[] keyValues = data.split(Const.ARRAY_PARSER_SPLIT);
        for (String keyValue : keyValues) {
            final String[] keyValuePair = keyValue.split(Const.KEY_VALUE_SPLIT);
            if (keyValuePair.length == 2) {
                this.data.put(keyValuePair[0], Long.parseLong(keyValuePair[1]));
            }
        }
    }

    @Override
    public void copyFrom(final DataTable source) {
        this.append(source);
    }

    public DataTable append(DataTable dataTable) {
        dataTable.data.forEach((key, value) -> {
            Long current = this.data.get(key);
            if (current == null) {
                current = value;
            } else {
                current += value;
            }
            this.data.put(key, current);
        });
        return this;
    }
}
