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

package org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.strategy;

import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.strategy.SegmentStatusStrategy.FROM_ENTRY_SPAN;
import static org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.strategy.SegmentStatusStrategy.FROM_FIRST_SPAN;
import static org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.strategy.SegmentStatusStrategy.FROM_SPAN_STATUS;

public class SegmentStatusAnalyzerTest {

    private SpanObject entryErrorSpan;
    private SpanObject entryNormalSpan;
    private SpanObject localFirstSpan;
    private SpanObject localErrorSpan;

    @BeforeEach
    public void prepare() {
        entryErrorSpan = SpanObject.newBuilder().setIsError(true).setSpanType(SpanType.Entry).setSpanId(0).build();
        entryNormalSpan = SpanObject.newBuilder().setIsError(false).setSpanType(SpanType.Entry).setSpanId(0).build();
        localErrorSpan = SpanObject.newBuilder().setIsError(true).setSpanType(SpanType.Local).setSpanId(1).build();
        localFirstSpan = SpanObject.newBuilder().setIsError(true).setSpanType(SpanType.Local).setSpanId(0).build();
    }

    @Test
    public void fromSpanStatus() {
        SegmentStatusAnalyzer exceptionAnalyzer = FROM_SPAN_STATUS.getExceptionAnalyzer();
        Assertions.assertTrue(exceptionAnalyzer.isError(entryErrorSpan));
        Assertions.assertFalse(exceptionAnalyzer.isError(entryNormalSpan));
        Assertions.assertTrue(exceptionAnalyzer.isError(localErrorSpan));
        Assertions.assertTrue(exceptionAnalyzer.isError(localFirstSpan));
    }

    @Test
    public void fromEntrySpan() {
        SegmentStatusAnalyzer exceptionAnalyzer = FROM_ENTRY_SPAN.getExceptionAnalyzer();
        Assertions.assertTrue(exceptionAnalyzer.isError(entryErrorSpan));
        Assertions.assertFalse(exceptionAnalyzer.isError(entryNormalSpan));
        Assertions.assertFalse(exceptionAnalyzer.isError(localErrorSpan));
        Assertions.assertFalse(exceptionAnalyzer.isError(localFirstSpan));
    }

    @Test
    public void fromFirstSpan() {
        SegmentStatusAnalyzer exceptionAnalyzer = FROM_FIRST_SPAN.getExceptionAnalyzer();
        Assertions.assertTrue(exceptionAnalyzer.isError(entryErrorSpan));
        Assertions.assertFalse(exceptionAnalyzer.isError(entryNormalSpan));
        Assertions.assertFalse(exceptionAnalyzer.isError(localErrorSpan));
        Assertions.assertTrue(exceptionAnalyzer.isError(localFirstSpan));
    }
}
