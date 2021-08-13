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

package org.apache.skywalking.banyandb.client.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.Map;

/**
 * BanyanDBEntity represents an entity returned from BanyanDB
 */
@Builder
@Getter
public class BanyanDBEntity {
    /**
     * fields are all indexed fields
     */
    @Singular
    private Map<String, Object> fields;

    /**
     * EntityId could be spanId of a Span or SegmentId of a Segment in the context of Trace
     */
    private final String entityId;

    /**
     * binaryData normally contains un-indexed fields
     */
    private byte[] binaryData;

    /**
     * timestamp represents
     * 1) either the start time of a Span/Segment,
     * 2) or the timestamp of a log
     */
    private final long timestampSeconds;
    private final int timestampNanoSeconds;
}
