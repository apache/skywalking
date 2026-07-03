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

package org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

@Slf4j
public class TextParserSpec extends AbstractParserSpec {
    private final ParseFailureWarnLimiter warnLimiter =
        new ParseFailureWarnLimiter(ParseFailureWarnLimiter.DEFAULT_INTERVAL_MS);

    public TextParserSpec(final ModuleManager moduleManager,
                          final LogAnalyzerModuleConfig moduleConfig) {
        super(moduleManager, moduleConfig);
    }

    public void regexp(final ExecutionContext ctx, final String regexp, final boolean abortOnFailure) {
        regexp(ctx, Pattern.compile(regexp), abortOnFailure);
    }

    public void regexp(final ExecutionContext ctx, final Pattern pattern, final boolean abortOnFailure) {
        if (ctx.shouldAbort()) {
            return;
        }
        final Object rawInput = ctx.input();
        if (!(rawInput instanceof LogData.Builder)) {
            // Typed-proto input (e.g. Envoy ALS) reaches a text{regexp} rule as a routing
            // mismatch, not a text body — honor abortOnFailure without a ClassCastException.
            if (abortOnFailure) {
                final String actual = rawInput == null ? "null" : rawInput.getClass().getSimpleName();
                ctx.dropReason("text parser: input is not a log body (expected LogData, got " + actual + ")");
                ctx.abort();
            }
            return;
        }
        final LogData.Builder logData = (LogData.Builder) rawInput;
        final Matcher matcher = pattern.matcher(logData.getBody().getText().getText());
        final boolean matched = matcher.find();
        if (matched) {
            ctx.parsed(matcher);
        } else if (abortOnFailure) {
            // Reason set only on the aborting path: a continued log is not dropped, and a
            // stale reason would leak onto a later abort {} statement.
            ctx.dropReason("text parser: regexp did not match the log body");
            final long suppressed = warnLimiter.acquire();
            if (suppressed >= 0) {
                log.warn("LAL text parser regexp did not match the log body (service={})"
                        + " ({} similar failures suppressed since the last report)",
                    ctx.metadata().getService(), suppressed);
            }
            ctx.abort();
        } else if (log.isDebugEnabled()) {
            log.debug("LAL text parser regexp did not match the log body (service={}, abortOnFailure=false)",
                ctx.metadata().getService());
        }
    }

}
