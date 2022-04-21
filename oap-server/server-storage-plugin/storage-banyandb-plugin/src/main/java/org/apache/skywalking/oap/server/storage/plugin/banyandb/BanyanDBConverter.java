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

package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.model.v1.BanyandbModel;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.StreamWrite;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.apache.skywalking.banyandb.v1.client.metadata.Serializable;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import java.util.List;
import java.util.function.Function;

public class BanyanDBConverter {
    @RequiredArgsConstructor
    public static class StreamToEntity implements Convert2Entity {
        private final RowEntity rowEntity;

        @Override
        public Object get(String fieldName) {
            return rowEntity.getTagValue(fieldName);
        }

        @Override
        public <T, R> R getWith(String fieldName, Function<T, R> typeDecoder) {
            return (R) this.get(fieldName);
        }
    }

    @Slf4j
    @RequiredArgsConstructor
    public static class StreamToStorage implements Convert2Storage<StreamWrite> {
        private final StreamWrite streamWrite;

        @Override
        public void accept(String fieldName, Object fieldValue) {
            // skip "time_bucket"
            if (Record.TIME_BUCKET.equals(fieldName)) {
                return;
            }
            try {
                this.streamWrite.tag(fieldName, buildTag(fieldValue));
            } catch (BanyanDBException ex) {
                log.error("fail to add tag", ex);
            }
        }

        private Serializable<BanyandbModel.TagValue> buildTag(Object value) {
            if (Integer.class.equals(value.getClass()) || Long.class.equals(value.getClass())) {
                return TagAndValue.longTagValue((long) value);
            } else if (String.class.equals(value.getClass())) {
                return TagAndValue.stringTagValue((String) value);
            }
            throw new IllegalStateException(value.getClass() + " is not supported");
        }

        @Override
        public void accept(String fieldName, byte[] fieldValue) {
            try {
                this.streamWrite.tag(fieldName, TagAndValue.binaryTagValue(fieldValue));
            } catch (BanyanDBException ex) {
                log.error("fail to add tag", ex);
            }
        }

        @Override
        public void accept(String fieldName, List<String> fieldValue) {
            for (final String tagKeyAndValue : fieldValue) {
                if (StringUtil.isEmpty(tagKeyAndValue)) {
                    continue;
                }
                int pos = tagKeyAndValue.indexOf("=");
                if (pos == -1) {
                    continue;
                }
                String key = tagKeyAndValue.substring(0, pos);
                String value = tagKeyAndValue.substring(pos + 1);
                this.accept(key, value);
            }
        }

        @Override
        public Object get(String fieldName) {
            // TODO: get an unmodifiable view of tag
            return null;
        }

        @Override
        public StreamWrite obtain() {
            return this.streamWrite;
        }
    }
}
