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
import org.apache.skywalking.plugin.test.agent.tool.validator.assertor.SegmentRefAssertFailedCause;
import org.apache.skywalking.plugin.test.agent.tool.validator.entity.SegmentRef;
import org.apache.skywalking.plugin.test.agent.tool.validator.exception.AssertFailedException;

public class SegmentRefNotFoundException extends AssertFailedException {
    private final SegmentRef expected;
    private final List<SegmentRefAssertFailedCause> causes;

    public SegmentRefNotFoundException(SegmentRef expected, List<SegmentRefAssertFailedCause> causes) {
        this.expected = expected;
        this.causes = causes;
    }

    @Override
    public String getCauseMessage() {

        StringBuilder actualMessage = new StringBuilder();
        for (SegmentRefAssertFailedCause cause : causes) {
            SegmentRef actual = cause.getActual();
            String reason = cause.getFailedCause().getCauseMessage();

            StringBuilder actualSegmentRef = new StringBuilder(String.format("\nSegmentRef:\t%s\n", reason));
            actualSegmentRef.append(String.format(" - entryServiceName:\t\t%s\n", actual.entryServiceName()))
                .append(String.format(" - networkAddress:\t\t\t%s\n", actual.networkAddress()))
                .append(String.format(" - parentServiceName:\t\t%s\n", actual.parentServiceName()))
                .append(String.format(" - parentSpanId:\t\t\t%s\n", actual.parentSpanId()))
                .append(String.format(" - parentTraceSegmentId:\t%s\n", actual.parentTraceSegmentId()))
                .append(String.format(" - refType:\t\t\t\t\t%s", actual.refType())).toString();

            actualMessage.append(String.format("%s\n", actualSegmentRef));
        }

        return String.format("SegmentRefNotFoundException\nexpected: %s\nactual: %s\n", expected, actualMessage);
    }
}
