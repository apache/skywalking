package org.skywalking.apm.agent.core.plugin.interceptor.enhance;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author wusheng
 */
public class InvokeContextTest {
    @Test
    public void testConstructorInvokeContext() {
        ConstructorInvokeContext context = new ConstructorInvokeContext(this, new Object[] {"obj1", 1});
        Assert.assertEquals(this, context.inst());
        Assert.assertEquals("obj1", context.allArguments()[0]);
        Assert.assertEquals(1, context.allArguments()[1]);
    }

    @Test
    public void testInstanceMethodInvokeContext() {
        InstanceMethodInvokeContext context = new InstanceMethodInvokeContext(this, "methodA", new Object[] {"obj1", 1}, new Class<?>[] {String.class, Integer.class});
        Assert.assertEquals(this, context.inst());
        Assert.assertEquals("obj1", context.allArguments()[0]);
        Assert.assertEquals(1, context.allArguments()[1]);
        Assert.assertEquals("methodA", context.methodName());
        Assert.assertEquals(String.class, context.argumentTypes()[0]);
        Assert.assertEquals(Integer.class, context.argumentTypes()[1]);
    }

    @Test
    public void testStaticMethodInvokeContext() {
        StaticMethodInvokeContext context = new StaticMethodInvokeContext(InvokeContextTest.class, "methodA", new Object[] {"obj1", 1}, new Class<?>[] {String.class, Integer.class});
        Assert.assertEquals(InvokeContextTest.class, context.claszz());
        Assert.assertEquals("obj1", context.allArguments()[0]);
        Assert.assertEquals(1, context.allArguments()[1]);
        Assert.assertEquals("methodA", context.methodName());
        Assert.assertEquals(String.class, context.argumentTypes()[0]);
        Assert.assertEquals(Integer.class, context.argumentTypes()[1]);
    }
}
