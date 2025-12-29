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

import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon;
import org.apache.skywalking.banyandb.model.v1.BanyandbModel;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.InvalidReferenceException;
import org.apache.skywalking.library.banyandb.v1.client.metadata.MetadataCache;
import org.apache.skywalking.library.banyandb.v1.client.metadata.Serializable;

public abstract class AbstractWrite<P extends com.google.protobuf.GeneratedMessageV3> {
    /**
     * Timestamp represents the time of the current data point, in milliseconds.
     * <p>
     * <b>When to set:</b>
     * <ul>
     *   <li><b>Stream and Measure writes:</b> This field <i>must</i> be set to indicate the event time.</li>
     *   <li><b>Trace writes:</b> This field is <i>not needed</i> and should be left unset; trace data does not require an explicit timestamp here.</li>
     * </ul>
     */
    @Getter
    protected Optional<Long> timestamp;

    protected final Object[] tags;

    @Getter
    protected final MetadataCache.EntityMetadata entityMetadata;

    public AbstractWrite(MetadataCache.EntityMetadata entityMetadata, long timestamp) {
        this(entityMetadata);
        this.timestamp = Optional.of(timestamp);
    }

    /**
     * Build a write request without initial timestamp.
     */
    AbstractWrite(MetadataCache.EntityMetadata entityMetadata) {
        if (entityMetadata == null) {
            throw new IllegalArgumentException("metadata not found");
        }
        this.entityMetadata = entityMetadata;
        this.tags = new Object[this.entityMetadata.getTotalTags()];
    }

    public AbstractWrite<P> tag(String tagName, Serializable<BanyandbModel.TagValue> tagValue) throws BanyanDBException {
        final Optional<MetadataCache.TagInfo> tagInfo = this.entityMetadata.findTagInfo(tagName);
        if (!tagInfo.isPresent()) {
            throw InvalidReferenceException.fromInvalidTag(tagName);
        }
        this.tags[tagInfo.get().getOffset()] = tagValue;
        return this;
    }

    public P build() {
        BanyandbCommon.Metadata metadata = BanyandbCommon.Metadata.newBuilder()
                .setGroup(entityMetadata.getGroup()).setName(entityMetadata.getName()).setModRevision(entityMetadata.getModRevision()).build();

        return build(metadata);
    }

    protected abstract P build(BanyandbCommon.Metadata metadata);

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("group=").append(entityMetadata.getGroup()).append(", ").append("name=")
                .append(entityMetadata.getName()).append(", ").append("timestamp=").append(timestamp).append(", ");
        for (int i = 0; i < this.tags.length; i++) {
            final int index = i;
            Map<String, MetadataCache.TagInfo> tagMap = this.entityMetadata.getTagOffset();
            Optional<String> tagName = tagMap.keySet().stream().filter(name -> tagMap.get(name).getOffset() == index).findAny();
            if (tagName.isPresent()) {
                stringBuilder.append(tagName.get()).append("=").append(((Serializable<BanyandbModel.TagValue>) this.tags[i]).serialize()).append(", ");
            }
        }
        return stringBuilder.toString();
    }
}
