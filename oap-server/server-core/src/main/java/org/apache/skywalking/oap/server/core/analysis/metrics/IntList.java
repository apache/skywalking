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

package org.apache.skywalking.oap.server.core.analysis.metrics;

import java.util.ArrayList;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataComplexObject;

/**
 * IntList is a serializable array list carrying int values.
 */
@ToString
@EqualsAndHashCode
public class IntList implements StorageDataComplexObject<IntList> {
    private List<Integer> data;

    public IntList(int initialSize) {
        this.data = new ArrayList(initialSize);
    }

    public IntList(String valueString) {
        toObject(valueString);
    }

    public int size() {
        return data.size();
    }

    public boolean include(int value) {
        return data.contains(value);
    }

    @Override
    public String toStorageData() {
        StringBuilder builder = new StringBuilder();

        this.data.forEach(element -> {
            if (builder.length() != 0) {
                // For the first element.
                builder.append(Const.ARRAY_SPLIT);
            }
            builder.append(element);
        });
        return builder.toString();
    }

    @Override
    public void toObject(final String data) {
        String[] elements = data.split(Const.ARRAY_PARSER_SPLIT);
        this.data = new ArrayList<>(elements.length);
        for (String element : elements) {
            this.data.add(Integer.parseInt(element));
        }
    }

    @Override
    public void copyFrom(final IntList source) {
        this.data.addAll(source.data);
    }

    public void add(final int rank) {
        this.data.add(rank);
    }

    public int get(final int idx) {
        return this.data.get(idx);
    }
}
