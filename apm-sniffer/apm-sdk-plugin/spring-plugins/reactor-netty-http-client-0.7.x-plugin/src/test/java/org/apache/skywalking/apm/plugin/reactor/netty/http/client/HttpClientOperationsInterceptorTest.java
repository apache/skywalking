package org.apache.skywalking.apm.plugin.reactor.netty.http.client;

import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.junit.*;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.*;

/**
 * @author jian.tan
 */
@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class HttpClientOperationsInterceptorTest {

    @Before
    public void setUp() throws Exception {
        ServiceManager.INSTANCE.boot();

    }

    @Test
    public void testHttpClient() throws Throwable {

    }
}
