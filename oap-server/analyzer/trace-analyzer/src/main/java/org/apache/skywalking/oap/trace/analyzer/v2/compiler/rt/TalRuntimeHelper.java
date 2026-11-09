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

package org.apache.skywalking.oap.trace.analyzer.v2.compiler.rt;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.source.TALFunctionContext;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.spec.FilterSpec;
import org.slf4j.Logger;

/**
 * The run of a generated rule method: its execution, the rule's services, and the script's logger. It is the
 * {@link TALFunctionContext} a function that takes the run receives first. What a rule and its generated code call is
 * split by role, see {@code TALRuntimeRoles}: the functions every input has, {@link TalGeneralFunctions} and
 * {@link TalConfigFunctions}, and what generated code runs, {@link TalValueConversions}, {@link TalMetricsRuntime} and
 * {@link TalNativeOutputs}. One instance per run.
 */
@Slf4j
public class TalRuntimeHelper implements TALFunctionContext {
    private final ExecutionContext ctx;
    private final FilterSpec spec;

    /**
     * The logger of the script the rule belongs to, {@code org.apache.skywalking.trace.analysis.script.<script>},
     * under which the functions that log write.
     */
    private final Logger scriptLog;

    public TalRuntimeHelper(final ExecutionContext ctx, final FilterSpec spec) {
        this(ctx, spec, log);
    }

    public TalRuntimeHelper(final ExecutionContext ctx, final FilterSpec spec, final Logger scriptLog) {
        this.ctx = ctx;
        this.spec = spec;
        this.scriptLog = scriptLog;
    }

    public ExecutionContext ctx() {
        return ctx;
    }

    /**
     * @return the rule's services, which the trace analyzer's own functions read
     */
    public FilterSpec spec() {
        return spec;
    }

    @Override
    public Object input() {
        return ctx.getInput();
    }

    @Override
    public Logger scriptLogger() {
        return scriptLog;
    }
}
