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

package org.apache.skywalking.oap.server.admin.dsl.debugging.session;

import org.apache.skywalking.oap.server.core.classloader.Catalog;
import org.apache.skywalking.oap.server.core.dsldebug.GateHolder;
import org.apache.skywalking.oap.server.core.dsldebug.RuleKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RecorderCapTest {

    @Test
    public void recordCap_flipsCapturedFlag() {
        final SessionLimits cap = new SessionLimits(3, 60_000L);
        final TestRecorder recorder = new TestRecorder(cap);

        recorder.pushExecution("payload-1");
        recorder.pushExecution("payload-2");
        assertFalse(recorder.isCaptured());

        recorder.pushExecution("payload-3");

        assertTrue(recorder.isCaptured(), "hitting recordCap must flip captured=true");
        assertEquals(3, recorder.snapshotRecords().size());

        // Further appends are ignored — gate is captured.
        recorder.pushExecution("ignored");
        assertEquals(3, recorder.snapshotRecords().size());
    }

    @Test
    public void appendingNullSample_isNoOp() {
        final TestRecorder recorder = new TestRecorder(SessionLimits.DEFAULT);
        recorder.pushNullSample();
        assertEquals(0, recorder.snapshotRecords().size());
        assertFalse(recorder.isCaptured());
    }

    @Test
    public void contentCarriedAtRecorderEnvelope() {
        final GateHolder holder = new GateHolder("rule-source-fixture");
        final TestRecorder recorder = new TestRecorder(SessionLimits.DEFAULT, holder);

        recorder.pushExecution("payload");

        // Per-record content has moved to the response-envelope `rule.dsl`; the
        // recorder still exposes the bound holder's content for the REST handler
        // to splice in. Records themselves carry only samples.
        assertEquals("rule-source-fixture", recorder.getContent(),
                     "recorder must expose the bound holder's verbatim source");
        assertEquals(1, recorder.snapshotRecords().size());
        assertEquals(1, recorder.snapshotRecords().get(0).getSamples().size());
    }

    private static final class TestRecorder extends AbstractDebugRecorder {
        TestRecorder(final SessionLimits limits) {
            this(limits, new GateHolder(""));
        }

        TestRecorder(final SessionLimits limits, final GateHolder holder) {
            super("session-test", new RuleKey(Catalog.OTEL_RULES, "f", "r"), holder, limits);
        }

        /** One execution = one sample + publish, mimicking a one-shot probe lifecycle. */
        void pushExecution(final String payload) {
            addSample(new Sample(Sample.TYPE_FUNCTION, "stage", true, "{\"v\":\"" + payload + "\"}", 0));
            publishCurrentExecution();
        }

        void pushNullSample() {
            addSample(null);
        }
    }
}
