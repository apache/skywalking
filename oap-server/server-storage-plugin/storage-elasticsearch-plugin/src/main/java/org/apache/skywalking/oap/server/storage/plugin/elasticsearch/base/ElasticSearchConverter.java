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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;

/**
 * ElasticSearchConverter same as the HashMapConverter, but translate the column alias automatically.
 */
public class ElasticSearchConverter {

    @RequiredArgsConstructor
    public static class ToEntity implements Convert2Entity {
        private final String modelName;
        private final Map<String, Object> source;

        @Override
        public Object get(final String fieldName) {
            return source.get(getPhysicalColumnName(modelName, fieldName));
        }

        @Override
        public byte[] getBytes(final String fieldName) {
            final String value = (String) source.get(getPhysicalColumnName(modelName, fieldName));
            if (StringUtil.isEmpty(value)) {
                return new byte[] {};
            }
            return Base64.getDecoder().decode(value);
        }
    }

    public static class ToStorage implements Convert2Storage<Map<String, Object>> {
        private Map<String, Object> source;
        private String modelName;

        public ToStorage(String modelName) {
            source = new HashMap();
            this.modelName = modelName;
        }

        @Override
        public void accept(final String fieldName, final Object fieldValue) {
            source.put(getPhysicalColumnName(modelName, fieldName)
                , fieldValue);
        }

        @Override
        public void accept(final String fieldName, final byte[] fieldValue) {
            if (CollectionUtils.isEmpty(fieldValue)) {
                source.put(getPhysicalColumnName(modelName, fieldName), Const.EMPTY_STRING);
            } else {
                source.put(getPhysicalColumnName(modelName, fieldName), new String(Base64.getEncoder().encode(fieldValue)));
            }
        }

        @Override
        public void accept(final String fieldName, final List<String> fieldValue) {
            this.accept(getPhysicalColumnName(modelName, fieldName), (Object) fieldValue);
        }

        @Override
        public Object get(final String fieldName) {
            return source.get(getPhysicalColumnName(modelName, fieldName));
        }

        @Override
        public Map<String, Object> obtain() {
            return source;
        }
    }

    private static String getPhysicalColumnName(String modelName, String fieldName) {
        return IndexController.LogicIndicesRegister.getPhysicalColumnName(modelName, fieldName);
    }
}
