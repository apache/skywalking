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

import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.IntKeyLongValuePair;

/**
 * IntKeyLongValue is a common bean, with key in Int and value in Long
 *
 * @author wusheng
 */
@Setter
@Getter
public class IntKeyLongValue implements Comparable<IntKeyLongValue> {
    private int key;
    private long value;

    public IntKeyLongValue() {
    }

    public IntKeyLongValue(int key, long value) {
        this.key = key;
        this.value = value;
    }

    public void addValue(long value) {
        this.value += value;
    }

    @Override
    public int compareTo(IntKeyLongValue o) {
        return key - o.key;
    }

    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        IntKeyLongValue value = (IntKeyLongValue)o;
        return key == value.key;
    }

    @Override public int hashCode() {
        return Objects.hash(key);
    }

    public IntKeyLongValuePair serialize() {
        return IntKeyLongValuePair.newBuilder().setKey(key).setValue(value).build();
    }

    public void deserialize(IntKeyLongValuePair pair) {
        this.key = pair.getKey();
        this.value = pair.getValue();
    }
}
