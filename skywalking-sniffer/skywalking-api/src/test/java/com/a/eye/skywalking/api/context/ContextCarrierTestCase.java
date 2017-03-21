package com.a.eye.skywalking.api.context;

import com.a.eye.skywalking.trace.TraceId.DistributedTraceId;
import com.a.eye.skywalking.trace.TraceId.PropagatedTraceId;
import java.util.LinkedList;
import java.util.List;
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
        carrier.setSampled(true);
        List<DistributedTraceId> ids = new LinkedList<DistributedTraceId>();
        ids.add(new PropagatedTraceId("Trace.global.id.123"));
        carrier.setDistributedTraceIds(ids);

        Assert.assertEquals("trace_id_A|100|REMOTE_APP|10.2.3.16:8080|Trace.global.id.123|1", carrier.serialize());
    }

    @Test
    public void testDeserialize(){
        ContextCarrier carrier = new ContextCarrier();
        carrier.deserialize("trace_id_A|100|REMOTE_APP|10.2.3.16:8080|Trace.global.id.123,Trace.global.id.222|1");

        Assert.assertEquals("trace_id_A", carrier.getTraceSegmentId());
        Assert.assertEquals(100, carrier.getSpanId());
        Assert.assertEquals("REMOTE_APP", carrier.getApplicationCode());
        Assert.assertEquals("10.2.3.16:8080", carrier.getPeerHost());
        Assert.assertEquals("Trace.global.id.123", carrier.getDistributedTraceIds().get(0).get());
        Assert.assertEquals("Trace.global.id.222", carrier.getDistributedTraceIds().get(1).get());
        Assert.assertEquals(true, carrier.isSampled());
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
        Assert.assertFalse(carrier.isValid());

        carrier = new ContextCarrier();
        carrier.deserialize("trace_id|100|REMOTE_APP|10.2.3.16:8080|Trace.global.id.123,Trace.global.id.222");
        Assert.assertFalse(carrier.isValid());

        carrier = new ContextCarrier();
        carrier.deserialize("trace_id|100|REMOTE_APP|10.2.3.16:8080|Trace.global.id.123,Trace.global.id.222|0");
        Assert.assertTrue(carrier.isValid());
    }
}
