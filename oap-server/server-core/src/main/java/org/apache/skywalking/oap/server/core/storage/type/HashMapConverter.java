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

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;

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
        public Object get(final String fieldName) {
            return source.get(fieldName);
        }

        @Override
        public Map<String, Object> obtain() {
            return source;
        }
    }
}
