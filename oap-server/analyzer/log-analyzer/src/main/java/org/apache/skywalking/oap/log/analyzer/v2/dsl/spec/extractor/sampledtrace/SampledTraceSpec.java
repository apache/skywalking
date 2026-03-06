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

package org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.extractor.sampledtrace;

import org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.AbstractSpec;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.SampledTraceBuilder;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

import static java.util.Objects.nonNull;

public class SampledTraceSpec extends AbstractSpec {
    public SampledTraceSpec(ModuleManager moduleManager, LogAnalyzerModuleConfig moduleConfig) {
        super(moduleManager, moduleConfig);
    }

    public void latency(final ExecutionContext ctx, final Long latency) {
        if (ctx.shouldAbort()) {
            return;
        }
        if (nonNull(latency)) {
            ctx.sampledTraceBuilder().setLatency(latency);
        }
    }

    public void uri(final ExecutionContext ctx, final String uri) {
        if (ctx.shouldAbort()) {
            return;
        }
        if (nonNull(uri)) {
            ctx.sampledTraceBuilder().setUri(uri);
        }
    }

    public void reason(final ExecutionContext ctx, final String reason) {
        if (ctx.shouldAbort()) {
            return;
        }
        if (nonNull(reason)) {
            ctx.sampledTraceBuilder().setReason(
                SampledTraceBuilder.Reason.valueOf(reason.toUpperCase()));
        }
    }

    public void processId(final ExecutionContext ctx, final String id) {
        if (ctx.shouldAbort()) {
            return;
        }
        if (nonNull(id)) {
            ctx.sampledTraceBuilder().setProcessId(id);
        }
    }

    public void destProcessId(final ExecutionContext ctx, final String id) {
        if (ctx.shouldAbort()) {
            return;
        }
        if (nonNull(id)) {
            ctx.sampledTraceBuilder().setDestProcessId(id);
        }
    }

    public void detectPoint(final ExecutionContext ctx, final String detectPoint) {
        if (ctx.shouldAbort()) {
            return;
        }
        if (nonNull(detectPoint)) {
            final DetectPoint point = DetectPoint.valueOf(detectPoint.toUpperCase());
            ctx.sampledTraceBuilder().setDetectPoint(point);
        }
    }

    public void componentId(final ExecutionContext ctx, final int id) {
        if (ctx.shouldAbort()) {
            return;
        }
        if (id > 0) {
            ctx.sampledTraceBuilder().setComponentId(id);
        }
    }

}