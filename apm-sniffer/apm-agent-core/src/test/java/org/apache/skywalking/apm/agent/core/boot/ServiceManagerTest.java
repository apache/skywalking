/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */


package org.apache.skywalking.apm.agent.core.boot;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.IgnoredTracerContext;
import org.apache.skywalking.apm.agent.core.context.TracingContext;
import org.apache.skywalking.apm.agent.core.context.TracingContextListener;
import org.apache.skywalking.apm.agent.core.jvm.JVMService;
import org.apache.skywalking.apm.agent.core.profile.ProfileTaskExecutionService;
import org.apache.skywalking.apm.agent.core.profile.ProfileTaskQueryService;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelListener;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelManager;
import org.apache.skywalking.apm.agent.core.remote.TraceSegmentServiceClient;
import org.apache.skywalking.apm.agent.core.sampling.SamplingService;
import org.apache.skywalking.apm.agent.core.test.tools.AgentServiceRule;
import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ServiceManagerTest {

    @Rule
    public AgentServiceRule agentServiceRule = new AgentServiceRule();

    @AfterClass
    public static void afterClass() {
        ServiceManager.INSTANCE.shutdown();
    }

    @Test
    public void testServiceDependencies() throws Exception {
        HashMap<Class, BootService> registryService = getFieldValue(ServiceManager.INSTANCE, "bootedServices");

        assertThat(registryService.size(), is(12));

        assertTraceSegmentServiceClient(ServiceManager.INSTANCE.findService(TraceSegmentServiceClient.class));
        assertContextManager(ServiceManager.INSTANCE.findService(ContextManager.class));
        assertGRPCChannelManager(ServiceManager.INSTANCE.findService(GRPCChannelManager.class));
        assertSamplingService(ServiceManager.INSTANCE.findService(SamplingService.class));
        assertJVMService(ServiceManager.INSTANCE.findService(JVMService.class));
        assertProfileTaskQueryService(ServiceManager.INSTANCE.findService(ProfileTaskQueryService.class));
        assertProfileTaskExecuteService(ServiceManager.INSTANCE.findService(ProfileTaskExecutionService.class));

        assertTracingContextListener();
        assertIgnoreTracingContextListener();
    }

    private void assertIgnoreTracingContextListener() throws Exception {
        List<TracingContextListener> listeners = getFieldValue(IgnoredTracerContext.ListenerManager.class, "LISTENERS");
        assertThat(listeners.size(), is(0));
    }

    private void assertTracingContextListener() throws Exception {
        List<TracingContextListener> listeners = getFieldValue(TracingContext.ListenerManager.class, "LISTENERS");
        assertThat(listeners.size(), is(1));

        assertThat(listeners.contains(ServiceManager.INSTANCE.findService(TraceSegmentServiceClient.class)), is(true));
    }

    private void assertJVMService(JVMService service) {
        assertNotNull(service);
    }

    private void assertProfileTaskQueryService(ProfileTaskQueryService service) {
        assertNotNull(service);
    }

    private void assertProfileTaskExecuteService(ProfileTaskExecutionService service) {
        assertNotNull(service);
    }

    private void assertGRPCChannelManager(GRPCChannelManager service) throws Exception {
        assertNotNull(service);

        List<GRPCChannelListener> listeners = getFieldValue(service, "listeners");
        assertEquals(listeners.size(), 4);
    }

    private void assertSamplingService(SamplingService service) {
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
