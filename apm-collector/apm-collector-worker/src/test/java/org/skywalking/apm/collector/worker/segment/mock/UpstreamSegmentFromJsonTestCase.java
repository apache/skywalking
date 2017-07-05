package org.skywalking.apm.collector.worker.segment.mock;

import com.google.gson.JsonArray;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.FileNotFoundException;
import java.util.LinkedList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.skywalking.apm.collector.worker.tools.JsonFileReader;
import org.skywalking.apm.network.proto.KeyWithStringValue;
import org.skywalking.apm.network.proto.SpanObject;
import org.skywalking.apm.network.proto.TraceSegmentObject;
import org.skywalking.apm.network.proto.UpstreamSegment;

/**
 * @author pengys5
 */
public class UpstreamSegmentFromJsonTestCase {

    @Test
    public void testBuild() throws FileNotFoundException, InvalidProtocolBufferException {
        JsonArray segmentArray = JsonFileReader.INSTANCE.parse(SegmentMock.PortalServiceJsonFile).getAsJsonArray();

        List<UpstreamSegment> upstreamSegmentList = new LinkedList<>();
        segmentArray.forEach(segmentJsonObj -> {
            UpstreamSegment upstreamSegment = UpstreamSegmentFromJson.INSTANCE.build(segmentJsonObj.getAsJsonObject());
            upstreamSegmentList.add(upstreamSegment);
        });

        Assert.assertEquals(1, upstreamSegmentList.size());
        UpstreamSegment upstreamSegment_1 = upstreamSegmentList.get(0);

        Assert.assertEquals(1, upstreamSegment_1.getGlobalTraceIdsCount());
        Assert.assertEquals("Trace.1490922929254.1797892356.6003.69.2", upstreamSegment_1.getGlobalTraceIds(0));

        TraceSegmentObject segmentObject = TraceSegmentObject.parseFrom(upstreamSegment_1.getSegment());
        Assert.assertEquals("Segment.1490922929254.1797892356.6003.69.1", segmentObject.getTraceSegmentId());
        Assert.assertEquals(1, segmentObject.getApplicationId());
        Assert.assertEquals(1, segmentObject.getApplicationInstanceId());

        Assert.assertEquals(1, segmentObject.getSpansCount());
        SpanObject span_0 = segmentObject.getSpans(0);
        Assert.assertEquals(0, span_0.getSpanId());
        Assert.assertEquals(-1, span_0.getParentSpanId());
        Assert.assertEquals(0, span_0.getOperationNameId());
        Assert.assertEquals("/portal/", span_0.getOperationName());
        Assert.assertEquals(0, span_0.getPeerId());
        Assert.assertEquals("0:0:0:0:0:0:0:1:57837", span_0.getPeer());
        Assert.assertEquals(0, span_0.getSpanTypeValue());
        Assert.assertEquals(2, span_0.getSpanLayerValue());
        Assert.assertEquals(0, span_0.getComponentId());
        Assert.assertEquals("Tomcat", span_0.getComponent());
        Assert.assertEquals(false, span_0.getIsError());

        Assert.assertEquals(2, span_0.getTagsCount());
        KeyWithStringValue tag_0 = span_0.getTags(0);
        Assert.assertEquals("key_1", tag_0.getKey());
        Assert.assertEquals("value_1", tag_0.getValue());

        KeyWithStringValue tag_1 = span_0.getTags(1);
        Assert.assertEquals("key_2", tag_1.getKey());
        Assert.assertEquals("value_2", tag_1.getValue());
    }
}
