package org.apache.skywalking.apm.util;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author lican
 * @date 2018/4/13
 */
public class RunnableWithExceptionProtectionTest {

    @Test
    public void testProtection() {
        Runnable worker = new Runnable() {
            @Override
            public void run() {
                throw new IllegalArgumentException(" unit test exception");
            }
        };
        RunnableWithExceptionProtection runnableWithExceptionProtection = new RunnableWithExceptionProtection(worker, new RunnableWithExceptionProtection.CallbackWhenException() {
            @Override
            public void handle(Throwable t) {
                Assert.assertNotNull(t.getMessage());
            }
        });
        new Thread(runnableWithExceptionProtection).start();

    }
}
