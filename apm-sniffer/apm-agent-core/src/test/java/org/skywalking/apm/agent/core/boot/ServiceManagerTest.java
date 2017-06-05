package org.skywalking.apm.agent.core.boot;

import org.junit.Assert;
import org.junit.Test;
import org.skywalking.apm.agent.core.context.ContextManager;

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
