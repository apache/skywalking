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
import java.util.Optional;
import java.util.TreeMap;
import lombok.Getter;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon;
import org.apache.skywalking.banyandb.model.v1.BanyandbModel;
import org.apache.skywalking.banyandb.stream.v1.BanyandbStream;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.apache.skywalking.library.banyandb.v1.client.metadata.Serializable;

/**
 * StreamWrite represents a write operation, including necessary fields, for {@link
 * BanyanDBClient#buildStreamWriteProcessor}.
 */
public class StreamWrite extends AbstractWrite<BanyandbStream.WriteRequest> {
    /**
     * ID of current entity
     */
    @Getter
    private final String elementId;

    // Use TreeMap to have a consistent order for tags
    private final Map<String/*tagFamily*/, Map<String/*tagName*/, Serializable<BanyandbModel.TagValue>/*tagValue*/>> tags;

    /**
     * Create a StreamWrite without initial timestamp.
     */
    StreamWrite(BanyandbCommon.Metadata entityMetadata, final String elementId) {
        super(entityMetadata);
        this.elementId = elementId;
        this.tags = new TreeMap<>();
    }

    public StreamWrite tag(String tagFamilyName, String tagName, Serializable<BanyandbModel.TagValue> tagValue) throws BanyanDBException {
        this.tags.computeIfAbsent(tagFamilyName, k -> new TreeMap<>()).put(tagName, tagValue);
        return this;
    }

    public void setTimestamp(long timestamp) {
        super.timestamp = Optional.of(timestamp);
    }

    /**
     * Build a write request
     *
     * @return {@link BanyandbStream.WriteRequest} for the bulk process.
     */
    @Override
    protected BanyandbStream.WriteRequest build(BanyandbCommon.Metadata metadata) {
        if (!timestamp.isPresent() || timestamp.get() <= 0) {
            throw new IllegalArgumentException("Timestamp is required and must be greater than 0 for stream writes.");
        }

        Timestamp ts = Timestamp.newBuilder()
                .setSeconds(timestamp.get() / 1000)
                .setNanos((int) (timestamp.get() % 1000 * 1_000_000)).build();

        final BanyandbStream.WriteRequest.Builder builder = BanyandbStream.WriteRequest.newBuilder();
        builder.setMetadata(metadata);
        final BanyandbStream.ElementValue.Builder elemValBuilder = BanyandbStream.ElementValue.newBuilder();
        elemValBuilder.setElementId(elementId);
        elemValBuilder.setTimestamp(ts);
        for (Map.Entry<String, Map<String, Serializable<BanyandbModel.TagValue>>> tagFamilyEntry : this.tags.entrySet()) {
            BanyandbStream.TagFamilySpec.Builder tagFamilySpecBuilder = BanyandbStream.TagFamilySpec.newBuilder();
            BanyandbModel.TagFamilyForWrite.Builder tagFamilyForWriteBuilder = BanyandbModel.TagFamilyForWrite.newBuilder();
            tagFamilySpecBuilder.setName(tagFamilyEntry.getKey());
            for (Map.Entry<String, Serializable<BanyandbModel.TagValue>> tagEntry : tagFamilyEntry.getValue().entrySet()) {
                tagFamilySpecBuilder.addTagNames(tagEntry.getKey());
                tagFamilyForWriteBuilder.addTags(tagEntry.getValue().serialize());
            }
            builder.addTagFamilySpec(tagFamilySpecBuilder);
            elemValBuilder.addTagFamilies(tagFamilyForWriteBuilder);
        }

        builder.setElement(elemValBuilder);
        builder.setMessageId(System.nanoTime());
        return builder.build();
    }

    /**
     * Build a write request without metadata and tag specs.
     *
     * @return {@link BanyandbStream.WriteRequest} for the bulk process.
     */
    @Override
    protected BanyandbStream.WriteRequest buildValues() {
        if (!timestamp.isPresent() || timestamp.get() <= 0) {
            throw new IllegalArgumentException("Timestamp is required and must be greater than 0 for stream writes.");
        }

        Timestamp ts = Timestamp.newBuilder()
                                .setSeconds(timestamp.get() / 1000)
                                .setNanos((int) (timestamp.get() % 1000 * 1_000_000)).build();

        final BanyandbStream.WriteRequest.Builder builder = BanyandbStream.WriteRequest.newBuilder();
        final BanyandbStream.ElementValue.Builder elemValBuilder = BanyandbStream.ElementValue.newBuilder();
        elemValBuilder.setElementId(elementId);
        elemValBuilder.setTimestamp(ts);
        for (Map.Entry<String, Map<String, Serializable<BanyandbModel.TagValue>>> tagFamilyEntry : this.tags.entrySet()) {
            BanyandbModel.TagFamilyForWrite.Builder tagFamilyForWriteBuilder = BanyandbModel.TagFamilyForWrite.newBuilder();
            for (Map.Entry<String, Serializable<BanyandbModel.TagValue>> tagEntry : tagFamilyEntry.getValue().entrySet()) {
                tagFamilyForWriteBuilder.addTags(tagEntry.getValue().serialize());
            }
            elemValBuilder.addTagFamilies(tagFamilyForWriteBuilder);
        }

        builder.setElement(elemValBuilder);
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
        return stringBuilder.toString();
    }
}
