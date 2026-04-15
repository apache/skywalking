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
 */

package org.apache.skywalking.oap.server.core.otel;

/**
 * Read-only abstraction over an OTLP span, decoupled from the protobuf
 * {@code io.opentelemetry.proto.trace.v1.Span} type so that {@code server-core}
 * does not depend on {@code receiver-proto}.
 *
 * <p>Implementations in the OTLP receiver module wrap the real proto object.
 */
public interface OTLPSpanReader {
    /**
     * @return the span name (e.g., "HTTP GET", "MXMetricPayload")
     */
    String spanName();

    /**
     * @return the span kind as a string (e.g., "CLIENT", "INTERNAL")
     */
    String spanKind();

    /**
     * @return start time in nanoseconds since epoch
     */
    long startTimeNanos();

    /**
     * @return end time in nanoseconds since epoch
     */
    long endTimeNanos();

    /**
     * Look up a span attribute by key.
     *
     * @return the attribute value as a string, or empty string if not found
     */
    String getAttribute(String key);
}
