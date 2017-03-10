package com.a.eye.skywalking.api.context;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by wusheng on 2017/2/19.
 */
public class ContextCarrierTestCase {
    @Test
    public void testSerialize(){
        ContextCarrier carrier = new ContextCarrier();
        carrier.setTraceSegmentId("trace_id_A");
        carrier.setSpanId(100);
        carrier.setApplicationCode("REMOTE_APP");
        carrier.setPeerHost("10.2.3.16:8080");

        Assert.assertEquals("trace_id_A|100|REMOTE_APP|10.2.3.16:8080", carrier.serialize());
    }

    @Test
    public void testDeserialize(){
        ContextCarrier carrier = new ContextCarrier();
        carrier.deserialize("trace_id_A|100|REMOTE_APP|10.2.3.16:8080");

        Assert.assertEquals("trace_id_A", carrier.getTraceSegmentId());
        Assert.assertEquals(100, carrier.getSpanId());
        Assert.assertEquals("REMOTE_APP", carrier.getApplicationCode());
        Assert.assertEquals("10.2.3.16:8080", carrier.getPeerHost());
    }

    @Test
    public void testIllegalDeserialize(){
        ContextCarrier carrier = new ContextCarrier();
        carrier.deserialize("abcde");
        Assert.assertFalse(carrier.isValid());

        carrier = new ContextCarrier();
        carrier.deserialize("trace_id|-100");
        Assert.assertFalse(carrier.isValid());

        carrier = new ContextCarrier();
        carrier.deserialize("trace_id|illegal-spanid");
        Assert.assertFalse(carrier.isValid());

        carrier = new ContextCarrier();
        carrier.deserialize("trace_id|100|other-illegal");
        Assert.assertFalse(carrier.isValid());

        carrier = new ContextCarrier();
        carrier.deserialize("trace_id|100|REMOTE_APP|10.2.3.16:8080");
        Assert.assertTrue(carrier.isValid());
    }
}
