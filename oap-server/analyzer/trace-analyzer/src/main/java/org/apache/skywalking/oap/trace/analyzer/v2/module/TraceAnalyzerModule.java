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

package org.apache.skywalking.oap.trace.analyzer.v2.module;

import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.spec.RateLimitingSamplers;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.ITraceAnalyzerService;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.KnownServices;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.SpanPairing;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.listener.SpanFilterListener;

public class TraceAnalyzerModule extends ModuleDefine {
    public static final String NAME = "trace-analyzer";

    public TraceAnalyzerModule() {
        super(NAME);
    }

    @Override
    public Class[] services() {
        return new Class[] {
            ITraceAnalyzerService.class,
            // TAL rule store — keyed by (Layer, ruleName), plus the `layer: auto` set. Each entry is a compiled
            // TAL rule that decides what a span is, extracts its identity, declares its metrics and votes on the
            // span's persistence. addOrReplace / remove mutate it after boot, reusing the compile helper the
            // startup path uses; no runtime-rule catalog drives them yet, so today the rules are the files read at start.
            SpanFilterListener.Factory.class,
            // The service names seen on spans, which the runtime's alias guards read.
            KnownServices.class,
            // The rateLimit counters, shared by id across every rule.
            RateLimitingSamplers.class,
            // The client and server spans waiting for their other half, which learns an alias per call pair.
            SpanPairing.class,
        };
    }
}
