package org.skywalking.apm.collector.worker.segment.entity;

import com.google.gson.stream.JsonReader;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;

/**
 * @author pengys5
 */
public class TraceSegmentRefTestCase {

    @Test
    public void deserialize() throws IOException {
        TraceSegmentRef traceSegmentRef = new TraceSegmentRef();
        JsonReader reader = new JsonReader(new StringReader("{\"ts\" :\"ts\",\"si\":0,\"ac\":\"ac\",\"ph\":\"ph\", \"skip\":\"skip\"}"));
        traceSegmentRef.deserialize(reader);

        Assert.assertEquals("ts", traceSegmentRef.getTraceSegmentId());
        Assert.assertEquals("ac", traceSegmentRef.getApplicationCode());
        Assert.assertEquals("ph", traceSegmentRef.getPeerHost());
        Assert.assertEquals(0, traceSegmentRef.getSpanId());
    }
}
