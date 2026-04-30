/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.log.analyzer.v2.module;

import org.apache.skywalking.oap.log.analyzer.v2.provider.log.ILogAnalyzerService;
import org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener.LogFilterListener;
import org.apache.skywalking.oap.meter.analyzer.v2.MalConverterRegistry;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;

public class LogAnalyzerModule extends ModuleDefine {
    public static final String NAME = "log-analyzer";

    public LogAnalyzerModule() {
        super(NAME);
    }

    @Override
    public Class[] services() {
        return new Class[] {
            ILogAnalyzerService.class,
            // LAL rule store — keyed by (Layer, ruleName). Each entry is a compiled LAL
            // {@code DSL} (the `LalExpression` class generated from a `lal/*.yaml` filter
            // block) that decides how to PARSE a log and EXTRACT fields. Owns the
            // `lal` runtime-rule catalog: hot-update mutates this Factory directly via
            // `addOrReplace` / `remove`, reusing the same `compile` helper the startup
            // path uses — no duplicate DSL wiring.
            LogFilterListener.Factory.class,
            // Inline-MAL converter store — keyed by string `<catalog>:<name>`. Each entry
            // is a `MetricConvert` compiled from a `log-mal-rules/*.yaml` rule that
            // AGGREGATES samples (emitted by LAL `metrics {}` blocks) into metrics. Owns
            // the `log-mal-rules` runtime-rule catalog. The same `MalConverterRegistry`
            // SPI lives in the meter-analyzer artifact (which is a library, not a
            // ModuleDefine); the OTel receiver implements the same interface for the
            // `otel-rules` catalog. Two implementations, two catalogs, one shared API.
            MalConverterRegistry.class,
        };
    }
}
