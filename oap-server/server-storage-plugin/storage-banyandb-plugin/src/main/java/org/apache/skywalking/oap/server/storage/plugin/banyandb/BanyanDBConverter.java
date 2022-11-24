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

import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.model.v1.BanyandbModel;
import org.apache.skywalking.banyandb.v1.client.DataPoint;
import org.apache.skywalking.banyandb.v1.client.MeasureWrite;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.StreamWrite;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.apache.skywalking.banyandb.v1.client.metadata.Serializable;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataComplexObject;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.util.ByteUtil;

import java.util.List;

public class BanyanDBConverter {
    public static class StorageToStream implements Convert2Entity {
        private final MetadataRegistry.Schema schema;
        private final RowEntity rowEntity;

        public StorageToStream(String streamModelName, RowEntity rowEntity) {
            this.schema = MetadataRegistry.INSTANCE.findRecordMetadata(streamModelName);
            this.rowEntity = rowEntity;
        }

        @Override
        public Object get(String fieldName) {
            MetadataRegistry.ColumnSpec spec = schema.getSpec(fieldName);
            if (double.class.equals(spec.getColumnClass())) {
                return ByteUtil.bytes2Double(rowEntity.getTagValue(fieldName));
            } else {
                return rowEntity.getTagValue(fieldName);
            }
        }

        @Override
        public byte[] getBytes(String fieldName) {
            return rowEntity.getTagValue(fieldName);
        }
    }

    @Slf4j
    @RequiredArgsConstructor
    public static class StreamToStorage implements Convert2Storage<StreamWrite> {
        private final MetadataRegistry.Schema schema;
        private final StreamWrite streamWrite;
        private final String timestampColumn;

        @Override
        public void accept(String fieldName, Object fieldValue) {
            if (fieldName.equals(timestampColumn)) {
                streamWrite.setTimestamp((long) fieldValue);
            }
            MetadataRegistry.ColumnSpec columnSpec = this.schema.getSpec(fieldName);
            if (columnSpec == null) {
                throw new IllegalArgumentException("fail to find field[" + fieldName + "]");
            }
            try {
                this.streamWrite.tag(fieldName, buildTag(fieldValue, columnSpec.getColumnClass()));
            } catch (BanyanDBException ex) {
                log.error("fail to add tag", ex);
            }
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
            try {
                this.streamWrite.tag(fieldName, TagAndValue.stringArrayTagValue(fieldValue));
            } catch (BanyanDBException ex) {
                log.error("fail to accept string array tag", ex);
            }
        }

        @Override
        public Object get(String fieldName) {
            throw new IllegalStateException("should not reach here");
        }

        @Override
        public StreamWrite obtain() {
            return this.streamWrite;
        }
    }

    @Slf4j
    @RequiredArgsConstructor
    public static class MeasureToStorage implements Convert2Storage<MeasureWrite> {
        private final MetadataRegistry.Schema schema;
        private final MeasureWrite measureWrite;

        @Override
        public void accept(String fieldName, Object fieldValue) {
            MetadataRegistry.ColumnSpec columnSpec = this.schema.getSpec(fieldName);
            if (columnSpec == null) {
                throw new IllegalArgumentException("fail to find field[" + fieldName + "]");
            }
            try {
                if (columnSpec.getColumnType() == MetadataRegistry.ColumnType.TAG) {
                    this.measureWrite.tag(fieldName, buildTag(fieldValue, columnSpec.getColumnClass()));
                } else {
                    this.measureWrite.field(fieldName, buildField(fieldValue, columnSpec.getColumnClass()));
                }
            } catch (BanyanDBException ex) {
                log.error("fail to add tag", ex);
            }
        }

        public void acceptID(String id) {
            try {
                this.measureWrite.setID(id);
            } catch (BanyanDBException ex) {
                log.error("fail to add ID tag", ex);
            }
        }

        @Override
        public void accept(String fieldName, byte[] fieldValue) {
            MetadataRegistry.ColumnSpec columnSpec = this.schema.getSpec(fieldName);
            try {
                if (columnSpec.getColumnType() == MetadataRegistry.ColumnType.TAG) {
                    this.measureWrite.tag(fieldName, TagAndValue.binaryTagValue(fieldValue));
                } else {
                    this.measureWrite.field(fieldName, TagAndValue.binaryFieldValue(fieldValue));
                }
            } catch (BanyanDBException ex) {
                log.error("fail to add binary tag/field", ex);
            }
        }

        @Override
        public void accept(String fieldName, List<String> fieldValue) {
            try {
                this.measureWrite.tag(fieldName, TagAndValue.stringArrayTagValue(fieldValue));
            } catch (BanyanDBException ex) {
                log.error("fail to accept string array tag", ex);
            }
        }

        @Override
        public Object get(String fieldName) {
            throw new IllegalStateException("should not reach here");
        }

        @Override
        public MeasureWrite obtain() {
            return this.measureWrite;
        }
    }

    private static Serializable<BanyandbModel.TagValue> buildTag(Object value, final Class<?> clazz) {
        if (int.class.equals(clazz) || Integer.class.equals(clazz)) {
            return TagAndValue.longTagValue(((Number) value).longValue());
        } else if (Long.class.equals(clazz) || long.class.equals(clazz)) {
            return TagAndValue.longTagValue((Long) value);
        } else if (String.class.equals(clazz)) {
            return TagAndValue.stringTagValue((String) value);
        } else if (Double.class.equals(clazz) || double.class.equals(clazz)) {
            return TagAndValue.binaryTagValue(ByteUtil.double2Bytes((double) value));
        } else if (StorageDataComplexObject.class.isAssignableFrom(clazz)) {
            return TagAndValue.stringTagValue(((StorageDataComplexObject<?>) value).toStorageData());
        } else if (clazz.isEnum()) {
            return TagAndValue.longTagValue((int) value);
        } else if (JsonObject.class.equals(clazz)) {
            return TagAndValue.stringTagValue((String) value);
        } else if (byte[].class.equals(clazz)) {
            return TagAndValue.stringTagValue((String) value);
        }
        throw new IllegalStateException(clazz.getSimpleName() + " is not supported");
    }

    private static Serializable<BanyandbModel.FieldValue> buildField(Object value, final Class<?> clazz) {
        if (Integer.class.equals(clazz) || int.class.equals(clazz)) {
            return TagAndValue.longFieldValue(((Number) value).longValue());
        } else if (Long.class.equals(clazz) || long.class.equals(clazz)) {
            return TagAndValue.longFieldValue((Long) value);
        } else if (String.class.equals(clazz)) {
            return TagAndValue.stringFieldValue((String) value);
        } else if (Double.class.equals(clazz) || double.class.equals(clazz)) {
            return TagAndValue.binaryFieldValue(ByteUtil.double2Bytes((double) value));
        } else if (StorageDataComplexObject.class.isAssignableFrom(clazz)) {
            return TagAndValue.stringFieldValue(((StorageDataComplexObject<?>) value).toStorageData());
        }
        throw new IllegalStateException(clazz.getSimpleName() + " is not supported");
    }

    public static class StorageToMeasure implements Convert2Entity {
        private final MetadataRegistry.Schema schema;
        private final DataPoint dataPoint;

        public StorageToMeasure(MetadataRegistry.Schema schema, DataPoint dataPoint) {
            this.schema = schema;
            this.dataPoint = dataPoint;
        }

        @Override
        public Object get(String fieldName) {
            MetadataRegistry.ColumnSpec spec = schema.getSpec(fieldName);
            switch (spec.getColumnType()) {
                case TAG:
                    if (double.class.equals(spec.getColumnClass())) {
                        return ByteUtil.bytes2Double(dataPoint.getTagValue(fieldName));
                    } else {
                        return dataPoint.getTagValue(fieldName);
                    }
                case FIELD:
                default:
                    if (double.class.equals(spec.getColumnClass())) {
                        return ByteUtil.bytes2Double(dataPoint.getFieldValue(fieldName));
                    } else {
                        return dataPoint.getFieldValue(fieldName);
                    }
            }
        }

        @Override
        public byte[] getBytes(String fieldName) {
            // TODO: double may be a field?
            return dataPoint.getFieldValue(fieldName);
        }
    }
}
