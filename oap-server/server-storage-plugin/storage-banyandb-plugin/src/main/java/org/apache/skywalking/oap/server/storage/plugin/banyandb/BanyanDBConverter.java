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
import org.apache.skywalking.library.banyandb.v1.client.DataPoint;
import org.apache.skywalking.library.banyandb.v1.client.MeasureWrite;
import org.apache.skywalking.library.banyandb.v1.client.RowEntity;
import org.apache.skywalking.library.banyandb.v1.client.StreamWrite;
import org.apache.skywalking.library.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.library.banyandb.v1.client.TraceWrite;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.apache.skywalking.library.banyandb.v1.client.metadata.Serializable;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileLanguageType;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageDataComplexObject;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.util.ByteUtil;

import java.util.List;

import static org.apache.skywalking.oap.server.core.storage.StorageData.ID;

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
            if (fieldName.equals(Record.TIME_BUCKET)) {
                final String timestampColumnName = schema.getTimestampColumn();
                long timestampMillis = ((Number) this.get(timestampColumnName)).longValue();
                return TimeBucket.getTimeBucket(timestampMillis, schema.getMetadata().getDownSampling());
            }
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

        @Override
        public void accept(String fieldName, Object fieldValue) {
            if (fieldName.equals(Record.TIME_BUCKET)) {
                return;
            }
            if (fieldName.equals(this.schema.getTimestampColumn())) {
                streamWrite.setTimestamp((long) fieldValue);
            }
            MetadataRegistry.ColumnSpec columnSpec = this.schema.getSpec(fieldName);
            if (columnSpec == null) {
                throw new IllegalArgumentException("fail to find tag[" + fieldName + "]");
            }
            if (columnSpec.getColumnType() != MetadataRegistry.ColumnType.TAG) {
                throw new IllegalArgumentException("ColumnType other than TAG is not supported for Stream, " +
                        "it should be an internal error, please submit an issue to the SkyWalking community");
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
            if (fieldName.equals(Metrics.TIME_BUCKET)) {
                return;
            }
            MetadataRegistry.ColumnSpec columnSpec = this.schema.getSpec(fieldName);
            if (columnSpec == null) {
                throw new IllegalArgumentException("fail to find tag/field[" + fieldName + "]");
            }
            try {
                if (columnSpec.getColumnType() == MetadataRegistry.ColumnType.TAG) {
                    this.measureWrite.tag(fieldName, buildTag(fieldValue, columnSpec.getColumnClass()));
                } else {
                    this.measureWrite.field(fieldName, buildField(fieldValue, columnSpec.getColumnClass()));
                }
            } catch (BanyanDBException ex) {
                log.error("fail to add tag/field", ex);
            }
        }

        public void acceptID(String id) {
            try {
                this.measureWrite.tag(ID, TagAndValue.stringTagValue(id));
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

    @Slf4j
    @RequiredArgsConstructor
    public static class TraceToStorage implements Convert2Storage<TraceWrite> {
        private final MetadataRegistry.Schema schema;
        private final TraceWrite traceWrite;

        @Override
        public void accept(String fieldName, Object fieldValue) {
            if (fieldName.equals(Record.TIME_BUCKET)) {
                return;
            }
            MetadataRegistry.ColumnSpec columnSpec = this.schema.getSpec(fieldName);
            // storage columns are not stored in tags
            if (columnSpec == null) {
                return;
            }
            if (columnSpec.getColumnType() != MetadataRegistry.ColumnType.TAG) {
                throw new IllegalArgumentException("ColumnType other than TAG is not supported for Trace, " +
                                                       "it should be an internal error, please submit an issue to the SkyWalking community");
            }
            try {
                String timestampColumn = schema.getTimestampColumn();
                if (fieldName.equals(timestampColumn)) {
                    this.traceWrite.tag(fieldName, TagAndValue.timestampTagValue((Long) fieldValue));
                } else {
                    this.traceWrite.tag(fieldName, buildTag(fieldValue, columnSpec.getColumnClass()));
                }
            } catch (BanyanDBException ex) {
                log.error("fail to add tag", ex);
            }
        }

        @Override
        public void accept(String fieldName, byte[] fieldValue) {
            MetadataRegistry.ColumnSpec columnSpec = this.schema.getSpec(fieldName);
            if (columnSpec == null) {
                return;
            }
            try {
                this.traceWrite.tag(fieldName, TagAndValue.binaryTagValue(fieldValue));
            } catch (BanyanDBException ex) {
                log.error("fail to add tag", ex);
            }
        }

        @Override
        public void accept(String fieldName, List<String> fieldValue) {
            MetadataRegistry.ColumnSpec columnSpec = this.schema.getSpec(fieldName);
            if (columnSpec == null) {
                return;
            }
            try {
                this.traceWrite.tag(fieldName, TagAndValue.stringArrayTagValue(fieldValue));
            } catch (BanyanDBException ex) {
                log.error("fail to accept string array tag", ex);
            }
        }

        @Override
        public Object get(String fieldName) {
            throw new IllegalStateException("should not reach here");
        }

        @Override
        public TraceWrite obtain() {
            return this.traceWrite;
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
        } else if (Layer.class.equals(clazz)) {
            return TagAndValue.longTagValue(((Integer) value).longValue());
        } else if (ProfileLanguageType.class.equals(clazz)) {
            // Mirror Layer handling: value is provided as Integer (enum ordinal/value)
            return TagAndValue.longTagValue(((Integer) value).longValue());
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
        } else if (Layer.class.equals(clazz)) {
            return TagAndValue.longFieldValue(((Integer) value).longValue());
        } else if (ProfileLanguageType.class.equals(clazz)) {
            // Mirror Layer handling: value is provided as Integer (enum ordinal/value)
            return TagAndValue.longFieldValue(((Integer) value).longValue());
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
            if (fieldName.equals(Metrics.TIME_BUCKET)) {
                return TimeBucket.getTimeBucket(dataPoint.getTimestamp(), schema.getMetadata().getDownSampling());
            }
            MetadataRegistry.ColumnSpec spec = schema.getSpec(fieldName);
            Class<?> clazz = spec.getColumnClass();
            switch (spec.getColumnType()) {
                case TAG:
                    Object tv = dataPoint.getTagValue(fieldName);
                    if (tv == null) {
                       return defaultValue(clazz);
                    }
                    if (double.class.equals(clazz)) {
                        return ByteUtil.bytes2Double(dataPoint.getTagValue(fieldName));
                    } else {
                        return dataPoint.getTagValue(fieldName);
                    }
                case FIELD:
                default:
                    Object fv = dataPoint.getFieldValue(fieldName);
                    if (fv == null) {
                        return defaultValue(clazz);
                    }
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

        private Object defaultValue(Class<?> clazz) {
            if (int.class.equals(clazz) || Integer.class.equals(clazz)) {
                return 0;
            } else if (Long.class.equals(clazz) || long.class.equals(clazz)) {
                return 0L;
            } else if (String.class.equals(clazz)) {
                return "";
            } else if (Double.class.equals(clazz) || double.class.equals(clazz)) {
                return 0D;
            } else if (StorageDataComplexObject.class.isAssignableFrom(clazz)) {
                return "";
            } else if (JsonObject.class.equals(clazz)) {
                return "";
            } else if (byte[].class.equals(clazz)) {
                return new byte[]{};
            }
            throw new IllegalStateException(clazz.getSimpleName() + " is not supported");
        }
    }
}
