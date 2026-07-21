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

package org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener;

import io.envoyproxy.envoy.data.accesslog.v3.HTTPAccessLogEntry;
import io.envoyproxy.envoy.data.accesslog.v3.TCPAccessLogEntry;
import java.util.Arrays;
import java.util.Collections;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.DSL;
import org.apache.skywalking.oap.server.core.source.LogMetadata;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Routing tests for {@link LogFilterListener#parse}: a rule that declares a
 * typed proto input only applies to logs of that type. Envoy HTTP and TCP
 * access logs share {@code Layer.MESH}, so the listener must dispatch each
 * entry to the rule whose input type matches and skip the others — otherwise
 * the mismatched rule throws {@code ClassCastException} on its generated proto
 * cast. Rules with a {@code null} input type (parser-based / untyped) run
 * against any input.
 *
 * <p>{@link DSL} is mocked so the assertions observe exactly which rules the
 * listener evaluated, independent of DSL compilation.
 */
class LogFilterListenerRoutingTest {

    private static LogMetadata meshMetadata() {
        return LogMetadata.builder().service("svc").layer("MESH").build();
    }

    private static DSL ruleWithInputType(final Class<?> inputType) {
        final DSL dsl = mock(DSL.class);
        // doReturn avoids the Class<?> wildcard-capture mismatch that
        // when(...).thenReturn(Class<?>) triggers on getInputType().
        doReturn(inputType).when(dsl).getInputType();
        return dsl;
    }

    @Test
    void tcpEntryReachesOnlyTcpAndParserRules() {
        // Mirrors the MESH bucket: envoy-als (HTTP), envoy-als-tcp (TCP), and
        // network-profiling-slow-trace (json parser, null inputType).
        final DSL httpRule = ruleWithInputType(HTTPAccessLogEntry.class);
        final DSL tcpRule = ruleWithInputType(TCPAccessLogEntry.class);
        final DSL parserRule = ruleWithInputType(null);

        final LogFilterListener listener = new LogFilterListener(
            Arrays.asList(httpRule, tcpRule, parserRule), false, null);

        listener.parse(meshMetadata(), TCPAccessLogEntry.newBuilder().build());
        listener.build();

        verify(tcpRule).evaluate(any());
        verify(parserRule).evaluate(any());
        verify(httpRule, never()).evaluate(any());
    }

    @Test
    void httpEntryReachesOnlyHttpAndParserRules() {
        final DSL httpRule = ruleWithInputType(HTTPAccessLogEntry.class);
        final DSL tcpRule = ruleWithInputType(TCPAccessLogEntry.class);
        final DSL parserRule = ruleWithInputType(null);

        final LogFilterListener listener = new LogFilterListener(
            Arrays.asList(httpRule, tcpRule, parserRule), false, null);

        listener.parse(meshMetadata(), HTTPAccessLogEntry.newBuilder().build());
        listener.build();

        verify(httpRule).evaluate(any());
        verify(parserRule).evaluate(any());
        verify(tcpRule, never()).evaluate(any());
    }

    @Test
    void untypedRuleRunsForAnyInput() {
        final DSL untyped = ruleWithInputType(null);

        final LogFilterListener listener = new LogFilterListener(
            Collections.singletonList(untyped), false, null);

        listener.parse(meshMetadata(), TCPAccessLogEntry.newBuilder().build());
        listener.build();

        verify(untyped).evaluate(any());
    }
}
