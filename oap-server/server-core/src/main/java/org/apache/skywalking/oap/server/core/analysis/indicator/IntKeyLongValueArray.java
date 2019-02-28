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

package org.apache.skywalking.oap.server.core.analysis.indicator;

import java.util.ArrayList;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataType;

/**
 * @author peng-yongsheng
 */
public class IntKeyLongValueArray extends ArrayList<IntKeyLongValue> implements StorageDataType {

    public IntKeyLongValueArray(int initialCapacity) {
        super(initialCapacity);
    }

    public IntKeyLongValueArray() {
        super(30);
    }

    public IntKeyLongValueArray(String data) {
        super();
        toObject(data);
    }

    @Override public String toStorageData() {
        StringBuilder data = new StringBuilder();
        for (int i = 0; i < this.size(); i++) {
            if (i == 0) {
                data.append(this.get(i).toStorageData());
            } else {
                data.append(Const.ARRAY_SPLIT).append(this.get(i).toStorageData());
            }
        }
        return data.toString();
    }

    @Override public void toObject(String data) {
        String[] keyValues = data.split(Const.ARRAY_PARSER_SPLIT);
        for (int i = 0; i < keyValues.length; i++) {
            IntKeyLongValue value = new IntKeyLongValue();
            value.toObject(keyValues[i]);
            this.add(value);
        }
    }

    @Override public void copyFrom(Object source) {
        IntKeyLongValueArray valueArray = (IntKeyLongValueArray)source;
        valueArray.forEach(value -> {
            IntKeyLongValue newValue = new IntKeyLongValue();
            newValue.copyFrom(value);
            this.add(newValue);
        });
    }
}
