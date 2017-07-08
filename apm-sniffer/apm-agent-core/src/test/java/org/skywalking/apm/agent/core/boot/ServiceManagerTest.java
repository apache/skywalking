package org.skywalking.apm.agent.core.boot;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.skywalking.apm.agent.core.boot.BootService;
import org.skywalking.apm.agent.core.boot.ServiceManager;
import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.context.IgnoredTracerContext;
import org.skywalking.apm.agent.core.context.TracingContext;
import org.skywalking.apm.agent.core.context.TracingContextListener;
import org.skywalking.apm.agent.core.jvm.JVMService;
import org.skywalking.apm.agent.core.remote.CollectorDiscoveryService;
import org.skywalking.apm.agent.core.remote.GRPCChannelListener;
import org.skywalking.apm.agent.core.remote.GRPCChannelManager;
import org.skywalking.apm.agent.core.remote.TraceSegmentServiceClient;
import org.skywalking.apm.agent.core.sampling.SamplingService;
import org.skywalking.apm.agent.core.test.tools.AgentServiceRule;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ServiceManagerTest {

    @Rule
    public AgentServiceRule agentServiceRule = new AgentServiceRule();

    @Test
    public void testServiceDependencies() throws Exception {
        HashMap<Class, BootService> registryService = getFieldValue(ServiceManager.INSTANCE, "bootedServices");

        assertThat(registryService.size(), is(7));

        assertTraceSegmentServiceClient(ServiceManager.INSTANCE.findService(TraceSegmentServiceClient.class));
        assertContextManager(ServiceManager.INSTANCE.findService(ContextManager.class));
        assertCollectorDiscoveryService(ServiceManager.INSTANCE.findService(CollectorDiscoveryService.class));
        assertGRPCChannelManager(ServiceManager.INSTANCE.findService(GRPCChannelManager.class));
        assertSamplingService(ServiceManager.INSTANCE.findService(SamplingService.class));
        assertJVMService(ServiceManager.INSTANCE.findService(JVMService.class));

        assertTracingContextListener();
        assertIgnoreTracingContextListener();
    }

    private void assertIgnoreTracingContextListener() throws Exception {
        List<TracingContextListener> LISTENERS = getFieldValue(IgnoredTracerContext.ListenerManager.class, "LISTENERS");
        assertThat(LISTENERS.size(), is(1));

        assertThat(LISTENERS.contains(ServiceManager.INSTANCE.findService(ContextManager.class)), is(true));
    }

    private void assertTracingContextListener() throws Exception {
        List<TracingContextListener> LISTENERS = getFieldValue(TracingContext.ListenerManager.class, "LISTENERS");
        assertThat(LISTENERS.size(), is(3));

        assertThat(LISTENERS.contains(ServiceManager.INSTANCE.findService(ContextManager.class)), is(true));
        assertThat(LISTENERS.contains(ServiceManager.INSTANCE.findService(TraceSegmentServiceClient.class)), is(true));
    }

    private void assertJVMService(JVMService service) {
        assertNotNull(service);
    }

    private void assertGRPCChannelManager(GRPCChannelManager service) throws Exception {
        assertNotNull(service);

        List<GRPCChannelListener> listeners = getFieldValue(service, "listeners");
        assertEquals(listeners.size(), 3);
    }

    private void assertSamplingService(SamplingService service) {
        assertNotNull(service);
    }

    private void assertCollectorDiscoveryService(CollectorDiscoveryService service) {
        assertNotNull(service);
    }

    private void assertContextManager(ContextManager service) {
        assertNotNull(service);
    }

    private void assertTraceSegmentServiceClient(TraceSegmentServiceClient service) {
        assertNotNull(service);
    }

    private <T> T getFieldValue(Object instance, String fieldName) throws Exception {
        Field field = instance.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (T)field.get(instance);
    }

    private <T> T getFieldValue(Class clazz, String fieldName) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (T)field.get(clazz);
    }

}
