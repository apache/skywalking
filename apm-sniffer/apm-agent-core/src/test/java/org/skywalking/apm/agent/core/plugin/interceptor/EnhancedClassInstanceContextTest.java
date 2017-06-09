package org.skywalking.apm.agent.core.plugin.interceptor;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author wusheng
 */
public class EnhancedClassInstanceContextTest {
    @Test
    public void test() {
        EnhancedClassInstanceContext context = new EnhancedClassInstanceContext();
        context.set("key", "value");
        Assert.assertTrue(context.isContain("key"));
        Assert.assertEquals("value", context.get("key"));
        Assert.assertEquals("value", (String)context.get("key"));
    }
}
