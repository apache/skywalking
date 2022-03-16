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

package org.apache.skywalking.oap.server.storage.plugin.jdbc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;

public class JDBCConverter {
    public static class RecordToStorage extends HashMapConverter.ToStorage {
        private Map<String, Object> notStoredSource = new HashMap<>(1);

        @Override
        public void accept(final String fieldName, final Object fieldValue) {
            super.accept(fieldName, fieldValue);
        }

        /**
         * Skip String list type column in SQL-style database. The values are processed by
         * AbstractSearchTagBuilder#analysisSearchTag(List, Convert2Storage) and TAGS_RAW_DATA column
         *
         * NOTICE, this method should only invoke once as mostly there is one tag list as properties for now.
         */
        @Override
        public void accept(final String fieldName, final List<String> fieldValue) {
            notStoredSource.put(fieldName, fieldValue);
        }

        @Override
        public Object get(final String fieldName) {
            final Object ret = super.get(fieldName);
            if (ret == null) {
                return notStoredSource.get(fieldName);
            }
            return ret;
        }
    }
}
