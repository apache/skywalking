package org.skywalking.apm.plugin.spring.mvc;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.skywalking.apm.agent.test.tools.TracingSegmentRunner;

import java.lang.reflect.Method;

import static org.powermock.api.mockito.PowerMockito.whenNew;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class PathMappingCacheTest {
    private PathMappingCache pathMappingCache;

    @Before
    public void setUp() throws Exception {
        pathMappingCache = new PathMappingCache("org.skywalking.apm.plugin.spring.mvc");
    }

    @Test
    public void testAddPathMapping1() throws Throwable {
        Object obj = new Object();
        Method m = obj.getClass().getMethods()[0];
        pathMappingCache.addPathMapping(m, "#toString");

        Assert.assertEquals("the two value should be equal", pathMappingCache.findPathMapping(m), "org.skywalking.apm.plugin.spring.mvc#toString");

    }
}
