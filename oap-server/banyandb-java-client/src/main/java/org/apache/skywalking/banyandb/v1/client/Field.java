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

package org.apache.skywalking.banyandb.v1.client;

import java.util.List;
import lombok.Getter;
import org.apache.skywalking.banyandb.v1.Banyandb;

import static com.google.protobuf.NullValue.NULL_VALUE;

/**
 * Field represents a value of column/field in the write-op or response.
 */
public abstract class Field<T> {
    @Getter
    protected final T value;

    protected Field(T value) {
        this.value = value;
    }

    /**
     * NullField is a value which can be converted to {@link com.google.protobuf.NullValue}.
     * Users should use the singleton instead of create a new instance everytime.
     */
    public static class NullField extends Field<Object> implements SerializableField {
        public static final NullField INSTANCE = new NullField();

        private NullField() {
            super(null);
        }

        @Override
        public Banyandb.Field toField() {
            return Banyandb.Field.newBuilder().setNull(NULL_VALUE).build();
        }
    }

    /**
     * The value of a String type field.
     */
    public static class StringField extends Field<String> implements SerializableField {
        public StringField(String value) {
            super(value);
        }

        @Override
        public Banyandb.Field toField() {
            return Banyandb.Field.newBuilder().setStr(Banyandb.Str.newBuilder().setValue(value)).build();
        }
    }

    /**
     * The value of a String array type field.
     */
    public static class StringArrayField extends Field<List<String>> implements SerializableField {
        public StringArrayField(List<String> value) {
            super(value);
        }

        @Override
        public Banyandb.Field toField() {
            return Banyandb.Field.newBuilder().setStrArray(Banyandb.StrArray.newBuilder().addAllValue(value)).build();
        }
    }

    /**
     * The value of an int64(Long) type field.
     */
    public static class LongField extends Field<Long> implements SerializableField {
        public LongField(Long value) {
            super(value);
        }

        @Override
        public Banyandb.Field toField() {
            return Banyandb.Field.newBuilder().setInt(Banyandb.Int.newBuilder().setValue(value)).build();
        }
    }

    /**
     * The value of an int64(Long) array type field.
     */
    public static class LongArrayField extends Field<List<Long>> implements SerializableField {
        public LongArrayField(List<Long> value) {
            super(value);
        }

        @Override
        public Banyandb.Field toField() {
            return Banyandb.Field.newBuilder().setIntArray(Banyandb.IntArray.newBuilder().addAllValue(value)).build();
        }
    }
}
