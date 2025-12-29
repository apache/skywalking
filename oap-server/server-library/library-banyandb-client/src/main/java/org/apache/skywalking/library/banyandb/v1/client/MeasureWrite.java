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
import java.util.Deque;
import java.util.LinkedList;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon;
import org.apache.skywalking.banyandb.measure.v1.BanyandbMeasure;
import org.apache.skywalking.banyandb.model.v1.BanyandbModel;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.apache.skywalking.library.banyandb.v1.client.metadata.MetadataCache;
import org.apache.skywalking.library.banyandb.v1.client.metadata.Serializable;

public class MeasureWrite extends AbstractWrite<BanyandbMeasure.WriteRequest> {
    /**
     * The values of "searchable" fields, which are defined by the schema.
     * In the bulk write process, BanyanDB client doesn't require field names anymore.
     */
    private final Object[] fields;

    MeasureWrite(MetadataCache.EntityMetadata entityMetadata, long timestamp) {
        super(entityMetadata, timestamp);
        this.fields = new Object[this.entityMetadata.getTotalFields()];
    }

    public MeasureWrite field(String fieldName, Serializable<BanyandbModel.FieldValue> fieldVal) {
        final int index = this.entityMetadata.findFieldInfo(fieldName);
        this.fields[index] = fieldVal;
        return this;
    }

    @Override
    public MeasureWrite tag(String tagName, Serializable<BanyandbModel.TagValue> tagValue) throws BanyanDBException {
        return (MeasureWrite) super.tag(tagName, tagValue);
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
        datapointValueBuilder.setTimestamp(ts);
        // memorize the last offset for the last tag family
        int lastFamilyOffset = 0;
        for (final int tagsPerFamily : this.entityMetadata.getTagFamilyCapacity()) {
            boolean firstNonNullTagFound = false;
            Deque<BanyandbModel.TagValue> tags = new LinkedList<>();
            for (int j = tagsPerFamily - 1; j >= 0; j--) {
                Object obj = this.tags[lastFamilyOffset + j];
                if (obj == null) {
                    if (firstNonNullTagFound) {
                        tags.addFirst(TagAndValue.nullTagValue().serialize());
                    }
                    continue;
                }
                firstNonNullTagFound = true;
                tags.addFirst(((Serializable<BanyandbModel.TagValue>) obj).serialize());
            }
            lastFamilyOffset += tagsPerFamily;
            datapointValueBuilder.addTagFamilies(BanyandbModel.TagFamilyForWrite.newBuilder().addAllTags(tags).build());
        }

        for (int i = 0; i < this.entityMetadata.getTotalFields(); i++) {
            Object obj = this.fields[i];
            if (obj != null) {
                datapointValueBuilder.addFields(((Serializable<BanyandbModel.FieldValue>) obj).serialize());
            } else {
                datapointValueBuilder.addFields(TagAndValue.nullFieldValue().serialize());
            }
        }

        builder.setDataPoint(datapointValueBuilder);
        builder.setMessageId(System.nanoTime());
        return builder.build();
    }
}
