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

package org.apache.skywalking.oap.query.traceql.rt;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

/**
 * TraceQL query parameters extracted from parsed query.
 */
@Data
public class TraceQLQueryParams {
    /**
     * Service name filter
     */
    private String serviceName;

    /**
     * Service instance name filter
     */
    private String serviceInstance;

    /**
     * Span name filter
     */
    private String spanName;

    /**
     * Minimum duration in microseconds (the Zipkin query API uses microseconds for duration)
     */
    private Long minDuration;

    /**
     * Maximum duration in microseconds (the Zipkin query API uses microseconds for duration)
     */
    private Long maxDuration;

    /**
     * Additional tag filters (resource, span, or intrinsic attributes)
     */
    private Map<String, String> tags = new HashMap<>();

    /**
     * HTTP status code filter
     */
    private String httpStatusCode;

    /**
     * Span kind filter
     */
    private String kind;

    /**
     * Status filter
     */
    private String status;
}
