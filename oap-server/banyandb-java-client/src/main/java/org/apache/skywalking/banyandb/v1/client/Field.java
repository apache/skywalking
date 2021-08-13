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
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.banyandb.v1.Banyandb;

import static com.google.protobuf.NullValue.NULL_VALUE;

/**
 * WriteField represents a value of column/field in the write-op or response.
 */
public interface Field {
    Banyandb.Field toField();

    class NullField implements Field {

        @Override
        public Banyandb.Field toField() {
            return Banyandb.Field.newBuilder().setNull(NULL_VALUE).build();
        }
    }

    /**
     * The value of a String type field.
     */
    @RequiredArgsConstructor
    @Getter
    class StringField implements Field {
        protected final String value;

        @Override
        public Banyandb.Field toField() {
            return Banyandb.Field.newBuilder().setStr(Banyandb.Str.newBuilder().setValue(value)).build();
        }
    }

    /**
     * The value of a String array type field.
     */
    @RequiredArgsConstructor
    @Getter
    class StringArrayField implements Field {
        protected final List<String> value;

        @Override
        public Banyandb.Field toField() {
            return Banyandb.Field.newBuilder().setStrArray(Banyandb.StrArray.newBuilder().addAllValue(value)).build();
        }
    }

    /**
     * The value of an int64(Long) type field.
     */
    @RequiredArgsConstructor
    @Getter
    class LongField implements Field {
        protected final Long value;

        @Override
        public Banyandb.Field toField() {
            return Banyandb.Field.newBuilder().setInt(Banyandb.Int.newBuilder().setValue(value)).build();
        }
    }

    /**
     * The value of an int64(Long) array type field.
     */
    @RequiredArgsConstructor
    @Getter
    class LongArrayField implements Field {
        protected final List<Long> value;

        @Override
        public Banyandb.Field toField() {
            return Banyandb.Field.newBuilder().setIntArray(Banyandb.IntArray.newBuilder().addAllValue(value)).build();
        }
    }
}
