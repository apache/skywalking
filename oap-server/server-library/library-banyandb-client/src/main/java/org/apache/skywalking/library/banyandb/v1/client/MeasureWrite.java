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

package org.apache.skywalking.library.banyandb.v1.client;

import com.google.protobuf.Timestamp;
import java.util.Map;
import java.util.TreeMap;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon;
import org.apache.skywalking.banyandb.measure.v1.BanyandbMeasure;
import org.apache.skywalking.banyandb.model.v1.BanyandbModel;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.apache.skywalking.library.banyandb.v1.client.metadata.Serializable;

public class MeasureWrite extends AbstractWrite<BanyandbMeasure.WriteRequest> {

    // Use TreeMap to have a consistent order for fields
    private final Map<String/*fieldName*/, Serializable<BanyandbModel.FieldValue>/*fieldValue*/> fields;

    // Use TreeMap to have a consistent order for tags
    private final Map<String/*tagFamily*/, Map<String/*tagName*/, Serializable<BanyandbModel.TagValue>/*tagValue*/>> tags;

    MeasureWrite(BanyandbCommon.Metadata entityMetadata, long timestamp) {
        super(entityMetadata, timestamp);
        this.fields = new TreeMap<>();
        this.tags = new TreeMap<>();
    }

    public MeasureWrite field(String fieldName, Serializable<BanyandbModel.FieldValue> fieldVal) {
        this.fields.put(fieldName, fieldVal);
        return this;
    }

    public MeasureWrite tag(String tagFamilyName, String tagName, Serializable<BanyandbModel.TagValue> tagValue) throws BanyanDBException {
        this.tags.computeIfAbsent(tagFamilyName, k -> new TreeMap<>()).put(tagName, tagValue);
        return this;
    }

    /**
     * Build a write request
     *
     * @return {@link BanyandbMeasure.WriteRequest} for the bulk process.
     */
    @Override
    protected BanyandbMeasure.WriteRequest build(BanyandbCommon.Metadata metadata) {
        if (!timestamp.isPresent() || timestamp.get() <= 0) {
            throw new IllegalArgumentException("Timestamp is required and must be greater than 0 for stream writes.");
        }
        Timestamp ts = Timestamp.newBuilder()
                .setSeconds(timestamp.get() / 1000)
                .setNanos((int) (timestamp.get() % 1000 * 1_000_000)).build();

        final BanyandbMeasure.WriteRequest.Builder builder = BanyandbMeasure.WriteRequest.newBuilder();
        builder.setMetadata(metadata);
        final BanyandbMeasure.DataPointValue.Builder datapointValueBuilder = BanyandbMeasure.DataPointValue.newBuilder();
        final BanyandbMeasure.DataPointSpec.Builder datapointValueSpecBuilder = BanyandbMeasure.DataPointSpec.newBuilder();
        datapointValueBuilder.setTimestamp(ts);
        for (Map.Entry<String, Map<String, Serializable<BanyandbModel.TagValue>>> tagFamilyEntry : this.tags.entrySet()) {
            BanyandbMeasure.TagFamilySpec.Builder tagFamilySpecBuilder = BanyandbMeasure.TagFamilySpec.newBuilder();
            BanyandbModel.TagFamilyForWrite.Builder tagFamilyForWriteBuilder = BanyandbModel.TagFamilyForWrite.newBuilder();
            tagFamilySpecBuilder.setName(tagFamilyEntry.getKey());
            for (Map.Entry<String, Serializable<BanyandbModel.TagValue>> tagEntry : tagFamilyEntry.getValue().entrySet()) {
                tagFamilySpecBuilder.addTagNames(tagEntry.getKey());
                tagFamilyForWriteBuilder.addTags(tagEntry.getValue().serialize());
            }
            datapointValueSpecBuilder.addTagFamilySpec(tagFamilySpecBuilder);
            datapointValueBuilder.addTagFamilies(tagFamilyForWriteBuilder);
        }

        for (Map.Entry<String, Serializable<BanyandbModel.FieldValue>> fieldEntry : this.fields.entrySet()) {
            datapointValueSpecBuilder.addFieldNames(fieldEntry.getKey());
            datapointValueBuilder.addFields(fieldEntry.getValue().serialize());
        }

        builder.setDataPointSpec(datapointValueSpecBuilder);
        builder.setDataPoint(datapointValueBuilder);
        builder.setMessageId(System.nanoTime());
        return builder.build();
    }

    /**
     * Build a write request without metadata and tag specs.
     *
     * @return {@link BanyandbMeasure.WriteRequest} for the bulk process.
     */
    @Override
    protected BanyandbMeasure.WriteRequest buildValues() {
        if (!timestamp.isPresent() || timestamp.get() <= 0) {
            throw new IllegalArgumentException("Timestamp is required and must be greater than 0 for stream writes.");
        }
        Timestamp ts = Timestamp.newBuilder()
                                .setSeconds(timestamp.get() / 1000)
                                .setNanos((int) (timestamp.get() % 1000 * 1_000_000)).build();

        final BanyandbMeasure.WriteRequest.Builder builder = BanyandbMeasure.WriteRequest.newBuilder();
        final BanyandbMeasure.DataPointValue.Builder datapointValueBuilder = BanyandbMeasure.DataPointValue.newBuilder();
        datapointValueBuilder.setTimestamp(ts);
        for (Map.Entry<String, Map<String, Serializable<BanyandbModel.TagValue>>> tagFamilyEntry : this.tags.entrySet()) {
            BanyandbModel.TagFamilyForWrite.Builder tagFamilyForWriteBuilder = BanyandbModel.TagFamilyForWrite.newBuilder();
            for (Map.Entry<String, Serializable<BanyandbModel.TagValue>> tagEntry : tagFamilyEntry.getValue().entrySet()) {
                tagFamilyForWriteBuilder.addTags(tagEntry.getValue().serialize());
            }
            datapointValueBuilder.addTagFamilies(tagFamilyForWriteBuilder);
        }

        for (Map.Entry<String, Serializable<BanyandbModel.FieldValue>> fieldEntry : this.fields.entrySet()) {
            datapointValueBuilder.addFields(fieldEntry.getValue().serialize());
        }

        builder.setDataPoint(datapointValueBuilder);
        builder.setMessageId(System.nanoTime());
        return builder.build();
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("group=").append(entityMetadata.getGroup()).append(", ").append("name=")
                     .append(entityMetadata.getName()).append(", ").append("timestamp=").append(timestamp).append(", ");
        for (Map.Entry<String, Map<String, Serializable<BanyandbModel.TagValue>>> entry : tags.entrySet()) {
            String tagFamilyName = entry.getKey();
            Map<String, Serializable<BanyandbModel.TagValue>> tagMap = entry.getValue();
            for (Map.Entry<String, Serializable<BanyandbModel.TagValue>> tagEntry : tagMap.entrySet()) {
                String tagName = tagEntry.getKey();
                Serializable<BanyandbModel.TagValue> tagValue = tagEntry.getValue();
                stringBuilder.append(tagFamilyName).append(".").append(tagName).append("=")
                             .append(tagValue.serialize()).append(", ");
            }
        }
        for (Map.Entry<String, Serializable<BanyandbModel.FieldValue>> fieldEntry : fields.entrySet()) {
            String fieldName = fieldEntry.getKey();
            Serializable<BanyandbModel.FieldValue> fieldValue = fieldEntry.getValue();
            stringBuilder.append("field.").append(fieldName).append("=")
                         .append(fieldValue.serialize()).append(", ");
        }
        return stringBuilder.toString();
    }
}
