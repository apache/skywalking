package com.a.eye.skywalking.collector.worker.span;

import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author pengys5
 */
public class SpanGetWithIdTestCase {

    @Test
    public void testRole() {
        Assert.assertEquals(SpanGetWithId.class.getSimpleName(), SpanGetWithId.WorkerRole.INSTANCE.roleName());
        Assert.assertEquals(RollingSelector.class.getSimpleName(), SpanGetWithId.WorkerRole.INSTANCE.workerSelector().getClass().getSimpleName());
    }
}
