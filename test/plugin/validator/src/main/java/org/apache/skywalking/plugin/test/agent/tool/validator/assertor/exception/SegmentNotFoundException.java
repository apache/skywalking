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
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.skywalking.plugin.test.agent.tool.validator.assertor.exception;

import java.util.List;
import org.apache.skywalking.plugin.test.agent.tool.validator.assertor.SegmentPredictionFailedCause;
import org.apache.skywalking.plugin.test.agent.tool.validator.entity.Segment;
import org.apache.skywalking.plugin.test.agent.tool.validator.entity.Span;
import org.apache.skywalking.plugin.test.agent.tool.validator.exception.AssertFailedException;

public class SegmentNotFoundException extends AssertFailedException {
    private final Segment expectedSegment;
    private final List<SegmentPredictionFailedCause> failedCauses;

    public SegmentNotFoundException(Segment expectedSegment, List<SegmentPredictionFailedCause> failedCauses) {
        this.expectedSegment = expectedSegment;
        this.failedCauses = failedCauses;
    }

    @Override
    public String getCauseMessage() {
        StringBuilder expectedMessage = new StringBuilder("\n  Segment:\n");
        for (Span span : expectedSegment.spans()) {
            expectedMessage.append(String.format("  - span[%s, %s] %s\n", span.parentSpanId(), span.spanId(),
                span.operationName()));
        }

        StringBuilder causeMessage = new StringBuilder();
        for (SegmentPredictionFailedCause cause : failedCauses) {
            Segment actualSegment = cause.getActualSegment();
            Span actualSpan = cause.getSpanAssertFailedCause().getActualSpan();
            Span expectedSpan = cause.getSpanAssertFailedCause().getExceptedSpan();

            causeMessage.append(String.format("\n  Segment[%s] e\n  expected:\tSpan[%s, %s] %s\n  " +
                    "actual:" +
                    "\tspan[%s, %s] %s\n  reason:\t%s\n",
                actualSegment.segmentId(),
                expectedSpan.parentSpanId(), expectedSpan.spanId(), expectedSpan.operationName(),
                actualSpan.parentSpanId(), actualSpan.spanId(), actualSpan.operationName(),
                cause.getSpanAssertFailedCause().getCauseMessage()));
        }

        return String.format("SegmentNotFoundException:\nexpected: %s\nactual: %s\n", expectedMessage, causeMessage);
    }
}
