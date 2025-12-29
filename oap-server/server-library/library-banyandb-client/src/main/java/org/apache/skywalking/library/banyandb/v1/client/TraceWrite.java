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

import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon;
import org.apache.skywalking.banyandb.model.v1.BanyandbModel;
import org.apache.skywalking.banyandb.trace.v1.BanyandbTrace;
import org.apache.skywalking.library.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.apache.skywalking.library.banyandb.v1.client.metadata.MetadataCache;
import org.apache.skywalking.library.banyandb.v1.client.metadata.Serializable;

/**
 * TraceWrite represents a write operation, including necessary fields, for {@link
 * BanyanDBClient#buildTraceWriteProcessor(int, int, int, int)}.
 */
public class TraceWrite extends AbstractWrite<BanyandbTrace.WriteRequest> {
    /**
     * Span data in binary format
     */
    @Getter
    private ByteString span;

    /**
     * Version for write request
     */
    @Getter
    private long version;

    /**
     * Create a TraceWrite without initial timestamp.
     */
    TraceWrite(MetadataCache.EntityMetadata entityMetadata) {
        super(entityMetadata);
        this.span = ByteString.EMPTY;
        this.version = 1L;
    }

    @Override
    public TraceWrite tag(String tagName, Serializable<BanyandbModel.TagValue> tagValue) throws BanyanDBException {
        return (TraceWrite) super.tag(tagName, tagValue);
    }

    /**
     * Set span data
     *
     * @param span span data in bytes
     */
    public TraceWrite span(byte[] span) {
        this.span = ByteString.copyFrom(span);
        return this;
    }

    /**
     * Set span data
     *
     * @param span span data as ByteString
     */
    public TraceWrite span(ByteString span) {
        this.span = span;
        return this;
    }

    /**
     * Set version
     *
     * @param version write request version
     */
    public TraceWrite version(long version) {
        this.version = version;
        return this;
    }

    /**
     * Build a write request
     *
     * @return {@link BanyandbTrace.WriteRequest} for the bulk process.
     */
    @Override
    protected BanyandbTrace.WriteRequest build(BanyandbCommon.Metadata metadata) {
        final BanyandbTrace.WriteRequest.Builder builder = BanyandbTrace.WriteRequest.newBuilder();
        builder.setMetadata(metadata);
        
        List<BanyandbModel.TagValue> tagValues = new ArrayList<>();
        for (int i = 0; i < this.tags.length; i++) {
            Object obj = this.tags[i];
            if (obj != null) {
                tagValues.add(((Serializable<BanyandbModel.TagValue>) obj).serialize());
            }
        }
        
        builder.addAllTags(tagValues);
        builder.setSpan(this.span);
        builder.setVersion(this.version);
        return builder.build();
    }
}