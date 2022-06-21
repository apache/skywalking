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

package org.apache.skywalking.oap.server.receiver.zipkin.handler;

import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.annotation.ConsumesJson;
import com.linecorp.armeria.server.annotation.ConsumesProtobuf;
import com.linecorp.armeria.server.annotation.Post;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.zipkin.ZipkinReceiverConfig;
import org.apache.skywalking.oap.server.receiver.zipkin.trace.SpanForward;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;
import static java.util.Objects.nonNull;

@Slf4j
public class ZipkinSpanHTTPHandler {
    private final HistogramMetrics histogram;
    private final CounterMetrics errorCounter;
    private final CounterMetrics msgIncr;
    private final SpanForward spanForward;

    public ZipkinSpanHTTPHandler(ZipkinReceiverConfig config, ModuleManager manager) {
        this.spanForward = new SpanForward(config, manager);
        MetricsCreator metricsCreator = manager.find(TelemetryModule.NAME)
                                               .provider()
                                               .getService(MetricsCreator.class);
        histogram = metricsCreator.createHistogramMetric(
            "zipkin_trace_in_latency", "The process latency of trace data",
            new MetricsTag.Keys("protocol"), new MetricsTag.Values("HTTP")
        );
        msgIncr = metricsCreator.createCounter(
            "zipkin_trace_received_count", "The number of zipkin trace received",
            new MetricsTag.Keys("protocol"), new MetricsTag.Values("HTTP"));
        errorCounter = metricsCreator.createCounter(
            "zipkin_trace_analysis_error_count", "The error number of trace analysis",
            new MetricsTag.Keys("protocol"), new MetricsTag.Values("HTTP")
        );
    }

    @Post("/api/v2/spans")
    public HttpResponse collectV2Spans(ServiceRequestContext ctx, HttpRequest req) {
        return doCollectSpans(SpanBytesDecoder.JSON_V2, ctx, req);
    }

    @Post("/api/v2/spans")
    @ConsumesJson
    public HttpResponse collectV2JsonSpans(ServiceRequestContext ctx, HttpRequest req) {
        return doCollectSpans(SpanBytesDecoder.JSON_V2, ctx, req);
    }

    @Post("/api/v2/spans")
    @ConsumesProtobuf
    public HttpResponse collectV2ProtobufSpans(ServiceRequestContext ctx, HttpRequest req) {
        return doCollectSpans(SpanBytesDecoder.PROTO3, ctx, req);
    }

    @Post("/api/v1/spans")
    public HttpResponse collectV1Spans(ServiceRequestContext ctx, HttpRequest req) {
        return doCollectSpans(SpanBytesDecoder.JSON_V1, ctx, req);
    }

    @Post("/api/v1/spans")
    @ConsumesJson
    public HttpResponse collectV1JsonSpans(ServiceRequestContext ctx, HttpRequest req) {
        return doCollectSpans(SpanBytesDecoder.JSON_V1, ctx, req);
    }

    @Post("/api/v1/spans")
    @ConsumesThrift
    public HttpResponse collectV1ThriftSpans(ServiceRequestContext ctx, HttpRequest req) {
        return doCollectSpans(SpanBytesDecoder.THRIFT, ctx, req);
    }

    HttpResponse doCollectSpans(final SpanBytesDecoder decoder,
                                final ServiceRequestContext ctx,
                                final HttpRequest req) {
        msgIncr.inc();
        final HistogramMetrics.Timer timer = histogram.createTimer();
        final HttpResponse response = HttpResponse.from(req.aggregate().thenApply(request -> {
            final HttpData httpData = UnzippingBytesRequestConverter.convertRequest(ctx, request);
            final List<Span> spanList = decoder.decodeList(httpData.byteBuf().nioBuffer());
            spanForward.send(spanList);
            return HttpResponse.of(HttpStatus.OK);
        }));
        response.whenComplete().handle((unused, throwable) -> {
            if (nonNull(throwable)) {
                errorCounter.inc();
            }
            timer.close();
            return null;
        });
        return response;
    }
}
