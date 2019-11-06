/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.skywalking.plugin.test.agent.tool.validator.assertor;

import java.util.ArrayList;
import java.util.List;

import org.apache.skywalking.plugin.test.agent.tool.validator.assertor.exception.SegmentRefSizeNotEqualsException;
import org.apache.skywalking.plugin.test.agent.tool.validator.assertor.exception.SegmentRefAssertFailedException;
import org.apache.skywalking.plugin.test.agent.tool.validator.assertor.exception.SegmentRefNotFoundException;
import org.apache.skywalking.plugin.test.agent.tool.validator.assertor.exception.ValueAssertFailedException;
import org.apache.skywalking.plugin.test.agent.tool.validator.entity.SegmentRef;

public class SegmentRefAssert {

    public static void assertEquals(List<SegmentRef> excepted, List<SegmentRef> actual) {
        if (excepted == null) {
            return;
        }

        if (actual == null || excepted.size() != actual.size()) {
            throw new SegmentRefSizeNotEqualsException(excepted.size(), actual.size());
        }

        for (SegmentRef ref : excepted) {
            findSegmentRef(actual, ref);
        }
    }

    private static SegmentRef findSegmentRef(List<SegmentRef> actual, SegmentRef expected) {
        List<SegmentRefAssertFailedCause> causes = new ArrayList<>();
        for (SegmentRef segmentRef : actual) {
            try {
                if (segmentRefEquals(expected, segmentRef)) {
                    return segmentRef;
                }
            } catch (SegmentRefAssertFailedException e) {
                causes.add(new SegmentRefAssertFailedCause(e, segmentRef));
            }
        }
        throw new SegmentRefNotFoundException(expected, causes);
    }

    private static boolean segmentRefEquals(SegmentRef expected, SegmentRef actual) {
        try {
            ExpressParser.parse(expected.entryServiceName()).assertValue("entry service name", actual.entryServiceName());
            ExpressParser.parse(expected.networkAddress()).assertValue("network address", actual.networkAddress());
            ExpressParser.parse(expected.parentTraceSegmentId()).assertValue("parent segment id", actual.parentTraceSegmentId());
            ExpressParser.parse(expected.parentSpanId()).assertValue("span id", actual.parentSpanId());
            ExpressParser.parse(expected.entryServiceId()).assertValue("entry service id", actual.entryServiceId());
            ExpressParser.parse(expected.networkAddressId()).assertValue("network address id", actual.networkAddressId());
            ExpressParser.parse(expected.parentApplicationInstanceId()).assertValue("parent application instance id", actual.parentApplicationInstanceId());
            ExpressParser.parse(expected.parentServiceId()).assertValue("parent service id", actual.parentServiceId());
            ExpressParser.parse(expected.parentServiceName()).assertValue("parent service name", actual.parentServiceName());
            ExpressParser.parse(expected.refType()).assertValue("ref type", actual.refType());
            ExpressParser.parse(expected.entryApplicationInstanceId()).assertValue("entry application instance id", actual.entryApplicationInstanceId());
            return true;
        } catch (ValueAssertFailedException e) {
            throw new SegmentRefAssertFailedException(e, expected, actual);
        }
    }
}
