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

import lombok.RequiredArgsConstructor;
import org.apache.skywalking.banyandb.v1.Banyandb;

import static com.google.protobuf.NullValue.NULL_VALUE;

/**
 * WriteField represents a value of column/field for a write-op value.
 */
public interface WriteField {
    Banyandb.Field toField();

    class NullField implements WriteField {

        @Override
        public Banyandb.Field toField() {
            return Banyandb.Field.newBuilder().setNull(NULL_VALUE).build();
        }
    }

    /**
     * The value of a String type field.
     */
    @RequiredArgsConstructor
    class StringField implements WriteField {
        private final String value;

        @Override
        public Banyandb.Field toField() {
            return Banyandb.Field.newBuilder().setStr(Banyandb.Str.newBuilder().setValue(value)).build();
        }
    }

    /**
     * The value of a String array type field.
     */
    @RequiredArgsConstructor
    class StringArrayField implements WriteField {
        private final String[] value;

        @Override
        public Banyandb.Field toField() {
            return null;
        }
    }

    /**
     * The value of an int64(Long) type field.
     */
    @RequiredArgsConstructor
    class LongField {
        private final long value;
    }

    /**
     * The value of an int64(Long) array type field.
     */
    @RequiredArgsConstructor
    class LongArrayField {
        private final long[] value;
    }
}
