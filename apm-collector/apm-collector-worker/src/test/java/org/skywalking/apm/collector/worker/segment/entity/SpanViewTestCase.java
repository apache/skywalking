package org.skywalking.apm.collector.worker.segment.entity;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author pengys5
 */
public class SpanViewTestCase {

    @Test
    public void test() {
        SpanView spanView = new SpanView();
        spanView.setSpanId(1);
        Assert.assertEquals(1, spanView.getSpanId());

        spanView.setSegId("1");
        Assert.assertEquals("1", spanView.getSegId());

        spanView.setAppCode("2");
        Assert.assertEquals("2", spanView.getAppCode());

        spanView.setRelativeStartTime(10L);
        Assert.assertEquals(10L, spanView.getRelativeStartTime());

        spanView.setCost(20L);
        Assert.assertEquals(20L, spanView.getCost());

        spanView.setOperationName("3");
        Assert.assertEquals("3", spanView.getOperationName());

        SpanView child = new SpanView();
        spanView.addChild(child);

        spanView.compareTo(child);
    }
}
