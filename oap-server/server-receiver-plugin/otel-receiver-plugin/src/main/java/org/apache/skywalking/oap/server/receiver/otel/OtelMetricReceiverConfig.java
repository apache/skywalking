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

package org.apache.skywalking.oap.server.receiver.otel;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

import java.util.List;

/**
 * The configuration of every OTLP handler. The name is historical; the trace and log handlers read it too.
 */
@Slf4j
public class OtelMetricReceiverConfig extends ModuleConfig {
    /**
     * The value of {@link #otlpTraceStorage} that stores OTLP spans natively in {@code otlp_span}.
     */
    public static final String OTLP_TRACE_STORAGE_OTLP = "otlp";
    /**
     * The value of {@link #otlpTraceStorage} that converts OTLP spans to Zipkin spans and hands them to the Zipkin
     * receiver, the behavior before native storage existed.
     */
    public static final String OTLP_TRACE_STORAGE_ZIPKIN = "zipkin";

    @Setter
    private String enabledHandlers;

    @Getter
    private String enabledOtelMetricsRules;

    /**
     * Where the {@code otlp-traces} handler stores spans, {@link #OTLP_TRACE_STORAGE_OTLP} (the default) or
     * {@link #OTLP_TRACE_STORAGE_ZIPKIN}.
     */
    @Getter
    @Setter
    private String otlpTraceStorage = OTLP_TRACE_STORAGE_OTLP;

    /**
     * Native mode only. Attribute keys offered by tag autocomplete. Every attribute is indexed for equality
     * search regardless of this list.
     */
    @Setter
    private String otlpTraceSearchableTags;

    /**
     * Native mode only. Head sampling by trace id, precision 1/10000.
     */
    @Getter
    @Setter
    private int otlpTraceSampleRate = 10000;

    /**
     * Native mode only. Spans per second accepted before dropping; 0 means no limit.
     */
    @Getter
    @Setter
    private int otlpTraceMaxSpansPerSecond = 0;

    public List<String> getEnabledHandlers() {
        return Splitter.on(",").trimResults().omitEmptyStrings().splitToList(Strings.nullToEmpty(enabledHandlers));
    }

    public List<String> getOtlpTraceSearchableTags() {
        return Splitter.on(",").trimResults().omitEmptyStrings().splitToList(Strings.nullToEmpty(otlpTraceSearchableTags));
    }

    public boolean isOtlpTraceStorageNative() {
        return OTLP_TRACE_STORAGE_OTLP.equalsIgnoreCase(Strings.nullToEmpty(otlpTraceStorage).trim());
    }

    /**
     * Whether {@link #otlpTraceStorage} names one of the two modes; anything else is a typo that must fail the boot
     * rather than silently select the Zipkin path.
     */
    public boolean isOtlpTraceStorageValid() {
        final String storage = Strings.nullToEmpty(otlpTraceStorage).trim();
        return OTLP_TRACE_STORAGE_OTLP.equalsIgnoreCase(storage) || OTLP_TRACE_STORAGE_ZIPKIN.equalsIgnoreCase(storage);
    }
}
