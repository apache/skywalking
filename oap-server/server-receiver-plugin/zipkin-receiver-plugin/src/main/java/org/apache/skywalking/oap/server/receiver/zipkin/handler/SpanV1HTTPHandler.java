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
import com.linecorp.armeria.server.annotation.Header;
import com.linecorp.armeria.server.annotation.Post;
import com.linecorp.armeria.server.annotation.StatusCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.zipkin.ZipkinReceiverConfig;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;
import zipkin2.codec.SpanBytesDecoder;

@Slf4j
public class SpanV1HTTPHandler {
    private final ZipkinReceiverConfig config;
    private final SourceReceiver sourceReceiver;
    private final NamingControl namingControl;
    private final HistogramMetrics histogram;
    private final CounterMetrics errorCounter;

    public SpanV1HTTPHandler(ZipkinReceiverConfig config, ModuleManager manager) {
        sourceReceiver = manager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
        namingControl = manager.find(CoreModule.NAME).provider().getService(NamingControl.class);
        this.config = config;
        MetricsCreator metricsCreator = manager.find(TelemetryModule.NAME)
                .provider()
                .getService(MetricsCreator.class);
        histogram = metricsCreator.createHistogramMetric(
                "trace_in_latency", "The process latency of trace data",
                new MetricsTag.Keys("protocol"), new MetricsTag.Values("zipkin-v1")
        );
        errorCounter = metricsCreator.createCounter("trace_analysis_error_count", "The error number of trace analysis",
                new MetricsTag.Keys("protocol"), new MetricsTag.Values("zipkin-v1")
        );
    }

    @Post("/api/v1/spans")
    @StatusCode(202)
    public void apiV1Spans(@Header("Content-Encoding") final String type,
                           final HttpData request) {
        try (HistogramMetrics.Timer ignored = histogram.createTimer()) {
            int encode = type != null && type.contains("/x-thrift") ? SpanEncode.THRIFT : SpanEncode.JSON_V1;

            SpanBytesDecoder decoder = SpanEncode.isThrift(encode) ? SpanBytesDecoder.THRIFT : SpanBytesDecoder.JSON_V1;

            SpanProcessor processor = new SpanProcessor(namingControl, sourceReceiver);
            processor.convert(config, decoder, type, request);
        } catch (Throwable e) {
            errorCounter.inc();
            throw new RuntimeException(e);
        }
    }
}
