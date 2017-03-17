package com.a.eye.skywalking.api.boot;

import com.a.eye.skywalking.api.context.ContextManager;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author wusheng
 */
public class ServiceManagerTest {
    @Test
    public void testBoot() {
        ServiceManager.INSTANCE.boot();
        ContextManager manager = ServiceManager.INSTANCE.findService(ContextManager.class);
        Assert.assertNotNull(manager);
    }
}
