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

package org.apache.skywalking.banyandb.client.request;

import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.List;

/**
 * Entity Write Request for BanyanBD
 */
@Builder
@Data
public class TraceWriteRequest {
    /**
     * the list contains all indexed fields to be written.
     * The order of this list must be strictly kept in order to comply with the fields defined in the schema.
     */
    @Singular
    private final List<WriteValue<?>> fields;

    /**
     * binary part of the entity
     */
    private byte[] dataBinary;

    /**
     * timestamp represents
     * 1) either the start time of a Span/Segment,
     * 2) or the timestamp of a log
     */
    private final long timestampSeconds;
    private int timestampNanos;

    /**
     * entityId could be spanId of a Span or segmentId of a Segment in the context of Trace
     */
    private final String entityId;
}
