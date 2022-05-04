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

package org.apache.skywalking.oap.server.core.storage.type;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;

/**
 * HashMapConverter represents a HashMap based converter to hold the value.
 *
 * This converter includes following converting rules.
 * 1. byte[] is converted to/from String through BASE64 encoder/decoder.
 */
public class HashMapConverter {
    /**
     * Stateful Hashmap based converter, build object from a HashMap type source.
     */
    @RequiredArgsConstructor
    public static class ToEntity implements Convert2Entity {
        private final Map<String, Object> source;

        @Override
        public Object get(final String fieldName) {
            return source.get(fieldName);
        }

        @Override
        public byte[] getBytes(final String fieldName) {
            final String value = (String) source.get(fieldName);
            if (StringUtil.isEmpty(value)) {
                return new byte[] {};
            }
            return Base64.getDecoder().decode(value);
        }
    }

    /**
     * Stateful Hashmap based converter, from object to HashMap.
     */
    public static class ToStorage implements Convert2Storage<Map<String, Object>> {
        private Map<String, Object> source;

        public ToStorage() {
            source = new HashMap();
        }

        @Override
        public void accept(final String fieldName, final Object fieldValue) {
            source.put(fieldName, fieldValue);
        }

        @Override
        public void accept(final String fieldName, final byte[] fieldValue) {
            if (CollectionUtils.isEmpty(fieldValue)) {
                source.put(fieldName, Const.EMPTY_STRING);
            } else {
                source.put(fieldName, new String(Base64.getEncoder().encode(fieldValue)));
            }
        }

        @Override
        public void accept(final String fieldName, final List<String> fieldValue) {
            this.accept(fieldName, (Object) fieldValue);
        }

        @Override
        public Object get(final String fieldName) {
            return source.get(fieldName);
        }

        @Override
        public Map<String, Object> obtain() {
            return source;
        }
    }
}
