package org.skywalking.apm.plugin.spring.mvc.define;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.skywalking.apm.agent.test.tools.TracingSegmentRunner;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class RestControllerInstrumentationTest {
    private RestControllerInstrumentation restControllerInstrumentation;

    @Before
    public void setUp() throws Exception {
        restControllerInstrumentation = new RestControllerInstrumentation();
    }

    @Test
    public void testGetEnhanceAnnotations() throws Throwable {
        Assert.assertArrayEquals(new String[] {restControllerInstrumentation.ENHANCE_ANNOTATION},
                restControllerInstrumentation.getEnhanceAnnotations());
    }

    @Test
    public void testGetInstanceMethodsInterceptPoints() throws Throwable {
        InstanceMethodsInterceptPoint[] methodPoints = restControllerInstrumentation.getInstanceMethodsInterceptPoints();
        assertThat(methodPoints.length, is(2));
        assertThat(methodPoints[0].getMethodsInterceptor(), is("org.skywalking.apm.plugin.spring.mvc.RequestMappingMethodInterceptor"));
        assertThat(methodPoints[1].getMethodsInterceptor(), is("org.skywalking.apm.plugin.spring.mvc.RestMappingMethodInterceptor"));

        Assert.assertFalse(methodPoints[0].isOverrideArgs());
        Assert.assertFalse(methodPoints[1].isOverrideArgs());

        Assert.assertNotNull(methodPoints[0].getMethodsMatcher());
        Assert.assertNotNull(methodPoints[1].getMethodsMatcher());

    }
}
