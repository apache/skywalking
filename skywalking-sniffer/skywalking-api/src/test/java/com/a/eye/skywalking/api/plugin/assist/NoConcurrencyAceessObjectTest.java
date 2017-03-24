package com.a.eye.skywalking.api.plugin.assist;

import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.api.plugin.interceptor.assist.NoConcurrencyAceessObject;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author wusheng
 */
public class NoConcurrencyAceessObjectTest {
    @Test
    public void testEntraExitCounter(){
        NoConcurrencyAceessObject object = new NoConcurrencyAceessObject();
        final EnhancedClassInstanceContext context = new EnhancedClassInstanceContext();
        object.whenEnter(context, new Runnable() {
            @Override
            public void run() {
                context.set("firstEntrance", true);
            }
        });
        object.whenEnter(context, new Runnable() {
            @Override
            public void run() {
                context.set("secondEntrance", true);
            }
        });
        object.whenExist(context, new Runnable() {
            @Override
            public void run() {
                context.set("firstExit", true);
            }
        });
        object.whenExist(context, new Runnable() {
            @Override
            public void run() {
                context.set("lastEntrance", true);
            }
        });

        Assert.assertTrue(!context.isContain("secondEntrance"));
        Assert.assertTrue(!context.isContain("firstExit"));
        Assert.assertTrue(context.isContain("firstEntrance"));
        Assert.assertTrue(context.isContain("lastEntrance"));
    }
}
