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
