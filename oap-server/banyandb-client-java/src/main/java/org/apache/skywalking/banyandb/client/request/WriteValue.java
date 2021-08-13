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

package org.apache.skywalking.banyandb.client.request;

import lombok.RequiredArgsConstructor;
import org.apache.skywalking.banyandb.Write;

/**
 * Abstract class of a Value linked to an entity to be written.
 * Provide typed API for writing.
 *
 * @param <T> the type of the field. It will be {@link Long} or {@link String}
 */
@RequiredArgsConstructor
public abstract class WriteValue<T> {
    /**
     * underlying storage field for Long/String/Null
     */
    protected final T value;

    /**
     * @return the protobuf representation of the WriteField
     */
    public abstract Write.Field toWriteField();

    /**
     * A Wrapper for {@link Write.Int}
     */
    public static class IntValue extends WriteValue<Long> {
        private IntValue(long value) {
            super(value);
        }

        @Override
        public Write.Field toWriteField() {
            return Write.Field.newBuilder().setInt(Write.Int.newBuilder().setValue(value).build()).build();
        }
    }

    /**
     * A Wrapper for {@link Write.Str}
     */
    private static class StrValue extends WriteValue<String> {
        private StrValue(String value) {
            super(value);
        }

        @Override
        public Write.Field toWriteField() {
            return Write.Field.newBuilder().setStr(Write.Str.newBuilder().setValue(value).build()).build();
        }
    }

    /**
     * A Null Wrapper for {@link Write.Field}
     */
    private static class NullValue extends WriteValue<Object> {
        private static final WriteValue<?> INSTANCE = new NullValue();

        private NullValue() {
            super(null);
        }

        @Override
        public Write.Field toWriteField() {
            return Write.Field.newBuilder().setNull(com.google.protobuf.NullValue.NULL_VALUE).build();
        }
    }

    public static WriteValue<?> intValue(long value) {
        return new IntValue(value);
    }

    public static WriteValue<?> strValue(String value) {
        return new StrValue(value);
    }

    public static WriteValue<?> nullValue() {
        return NullValue.INSTANCE;
    }
}
