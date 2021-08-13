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

package org.apache.skywalking.banyandb.v1.client;

import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * TraceWrite represents a write operation, including necessary fields, for {@link
 * BanyanDBClient#writeTrace(TraceWrite)}.
 */
@Builder
@Getter(AccessLevel.PROTECTED)
public class TraceWrite {
    /**
     * Ower name current entity
     */
    private final String name;
    /**
     * ID of current entity
     */
    private final String entityId;
    /**
     * Timestamp represents the time of current trace or trace segment.
     */
    private final long timestamp;
    /**
     * The binary raw data represents the whole object of current trace or trace segment. It could be organized by
     * different serialization formats. Natively, SkyWalking uses protobuf, but it is not required. The BanyanDB server
     * wouldn't deserialize this. So, no format requirement.
     */
    private final byte[] binary;
    private final List<WriteField> fields;

}
