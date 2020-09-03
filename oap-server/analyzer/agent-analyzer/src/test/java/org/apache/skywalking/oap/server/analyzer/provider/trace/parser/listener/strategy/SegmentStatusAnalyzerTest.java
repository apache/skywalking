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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class SegmentStatusAnalyzerTest {

    private SpanObject entryErrorSpan;
    private SpanObject entryNormalSpan;
    private SpanObject localNormalSpan;
    private SpanObject localErrorSpan;

    @Before
    public void prepare() {
        entryErrorSpan = SpanObject.newBuilder().setIsError(true).setSpanType(SpanType.Entry).build();
        entryNormalSpan = SpanObject.newBuilder().setIsError(false).setSpanType(SpanType.Entry).build();
        localErrorSpan = SpanObject.newBuilder().setIsError(true).setSpanType(SpanType.Local).build();
        localNormalSpan = SpanObject.newBuilder().setIsError(false).setSpanType(SpanType.Local).build();
    }

    @Test
    public void fromEntrySpan() {
        FromEntrySpan fromEntrySpan = new FromEntrySpan();
        Assert.assertTrue(fromEntrySpan.isError(entryErrorSpan));
        Assert.assertFalse(fromEntrySpan.isError(entryNormalSpan));
        Assert.assertFalse(fromEntrySpan.isError(localNormalSpan));
        Assert.assertFalse(fromEntrySpan.isError(localErrorSpan));
    }

    @Test
    public void fromSpanStatus() {
        FromSpanStatus fromSpanStatus = new FromSpanStatus();
        Assert.assertTrue(fromSpanStatus.isError(entryErrorSpan));
        Assert.assertFalse(fromSpanStatus.isError(entryNormalSpan));
        Assert.assertFalse(fromSpanStatus.isError(localNormalSpan));
        Assert.assertTrue(fromSpanStatus.isError(localErrorSpan));
    }
}