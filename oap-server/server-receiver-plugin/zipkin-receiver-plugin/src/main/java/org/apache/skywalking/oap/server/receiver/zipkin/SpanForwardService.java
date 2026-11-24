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

package org.apache.skywalking.oap.server.receiver.zipkin;

import org.apache.skywalking.oap.server.core.trace.TraceInLatency;
import org.apache.skywalking.oap.server.core.trace.TraceSpansDropped;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import zipkin2.Span;

import java.util.List;

public interface SpanForwardService extends Service {
    /**
     * Sample, rate-limit and process Zipkin spans. One span that fails to be processed is dropped alone; the rest
     * are processed.
     *
     * @param spanLatency  observes each span processed or failed, in seconds times
     *                     {@link TraceInLatency#SPAN_LATENCY_SCALE}; a span dropped by sampling or the rate limit is
     *                     not observed
     * @param droppedSpans counts the spans dropped by sampling, the rate limit or a processing error, for the
     *                     protocol the spans came in on
     */
    void send(List<Span> spanList, HistogramMetrics spanLatency, TraceSpansDropped droppedSpans);
}
