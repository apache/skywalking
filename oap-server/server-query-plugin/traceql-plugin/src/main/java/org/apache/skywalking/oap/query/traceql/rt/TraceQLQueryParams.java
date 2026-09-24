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
    private static final String[] SCOPE_PREFIXES = {"resource.", "span."};

    /**
     * Service name filter
     */
    private String serviceName;

    /**
     * remote service name filter, only for Zipkin traces
     */
    private String remoteServiceName;

    /**
     * Service instance name filter, only for SkyWalking native traces
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
     * Attribute equality filters keyed as written, {@code resource.env}, {@code span.env} or the unscoped
     * {@code env}. The OTLP datasource matches each in its own scope; the others take {@link #flatTags()}.
     */
    private Map<String, String> tags = new HashMap<>();

    /**
     * @return the tags with the {@code resource.} and {@code span.} scopes stripped, for the Zipkin and SkyWalking
     * datasources whose tag indexes have no scopes
     */
    public Map<String, String> flatTags() {
        final Map<String, String> flat = new HashMap<>(tags.size());
        for (final Map.Entry<String, String> tag : tags.entrySet()) {
            String key = tag.getKey();
            for (final String scope : SCOPE_PREFIXES) {
                if (key.startsWith(scope)) {
                    key = key.substring(scope.length());
                    break;
                }
            }
            flat.put(key, tag.getValue());
        }
        return flat;
    }

    /**
     * HTTP status code filter
     */
    private String httpStatusCode;

    /**
     * Status filter
     */
    private String status;

    /**
     * Span kind filter, the TraceQL spelling: {@code server}, {@code client}, {@code producer}, {@code consumer},
     * {@code internal} or {@code unspecified}. Served by the OTLP datasource only.
     */
    private String kind;
}
