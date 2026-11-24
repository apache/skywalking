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

package org.apache.skywalking.oap.server.core.trace;

/**
 * The {@code trace_analysis_error_count} counter, registered by every trace receiver with its own {@code protocol}
 * label. The Prometheus telemetry keeps the help text of the first registration for the whole metric, so every
 * registration uses {@link #HELP}. A span dropped on its own is counted in {@link TraceSpansDropped} instead.
 */
public final class TraceAnalysisError {
    public static final String NAME = "trace_analysis_error_count";

    public static final String HELP = "The count of received trace data that could not be decoded or processed as a "
        + "whole, by protocol: a SkyWalking agent segment (grpc, http, kafka), a Zipkin HTTP request or Kafka message "
        + "(zipkin-http, zipkin-kafka), an OTLP export request (otlp-grpc, otlp-http). zipkin-kafka also counts the "
        + "errors of polling Kafka.";

    private TraceAnalysisError() {
    }
}
