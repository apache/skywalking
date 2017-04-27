package com.a.eye.skywalking.api.plugin.assist;

import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.api.plugin.interceptor.assist.NoConcurrencyAccessObject;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author wusheng
 */
@RunWith(MockitoJUnitRunner.class)
public class NoConcurrencyAccessObjectTest {

    @Mock
    private InstanceMethodInvokeContext invokeContext;

    @Test
    public void testEntraExitCounter() {
        final EnhancedClassInstanceContext context = new EnhancedClassInstanceContext();
        NoConcurrencyAccessObject first = new NoConcurrencyAccessObject(){

            @Override
            protected void enter(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext) {
                context.set("firstEntrance", true);
            }

            @Override protected void exit() {
                context.set("firstExit", true);
            }
        };

        NoConcurrencyAccessObject second = new NoConcurrencyAccessObject(){

            @Override
            protected void enter(EnhancedClassInstanceContext context, InstanceMethodInvokeContext interceptorContext) {
                context.set("secondEntrance", true);
            }

            @Override protected void exit() {
                context.set("lastEntrance", true);
            }
        };

        first.whenEnter(context, invokeContext);
        second.whenEnter(context, invokeContext);
        first.whenExist(context);
        second.whenExist(context);

        Assert.assertTrue(!context.isContain("secondEntrance"));
        Assert.assertTrue(!context.isContain("firstExit"));
        Assert.assertTrue(context.isContain("firstEntrance"));
        Assert.assertTrue(context.isContain("lastEntrance"));
    }
}
