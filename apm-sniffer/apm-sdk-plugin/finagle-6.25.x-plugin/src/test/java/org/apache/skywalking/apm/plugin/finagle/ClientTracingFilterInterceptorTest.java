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

package org.apache.skywalking.apm.plugin.finagle;

import com.twitter.finagle.Address;
import com.twitter.finagle.Addresses$;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.ExitTypeSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import java.net.Inet4Address;
import java.net.InetSocketAddress;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class ClientTracingFilterInterceptorTest extends AbstractTracingFilterTest {

    @SegmentStoragePoint
    protected SegmentStorage segmentStorage;

    private ClientTracingFilterInterceptor clientTracingFilterInterceptor;
    private ClientDestTracingFilterInterceptor clientDestTracingFilterInterceptor =
            new ClientDestTracingFilterInterceptor();
    private EnhancedInstance enhancedClientDestTracingFilter = new MockEnhancedInstance();
    private InetSocketAddress serverSocketAddress;
    private Address serverAddr;

    @Override
    protected void prepareForTest() throws Throwable {
        clientTracingFilterInterceptor = new ClientTracingFilterInterceptor();
        serverSocketAddress = new InetSocketAddress(Inet4Address.getLocalHost(), 9999);
        serverAddr = Addresses$.MODULE$.newInetAddress(serverSocketAddress);
    }

    @Override
    protected void assertSpan(AbstractTracingSpan span) {
        assertTrue(span.isExit());
        assertThat(((ExitTypeSpan) span).getPeer(), is(serverSocketAddress.getAddress().getHostAddress() + ":" + serverSocketAddress.getPort()));
    }

    @Test
    public void testClient() throws Throwable {
        clientTracingFilterInterceptor.beforeMethod(enhancedInstance, null, allArguments, argumentTypes, methodInterceptResult);
        result.setValue(new Object());
        rpcInterceptor.onConstruct(null, new Object[]{rpc});
        clientDestTracingFilterInterceptor.onConstruct(enhancedClientDestTracingFilter, new Object[]{serverAddr});

        clientDestTracingFilterInterceptor.beforeMethod(enhancedClientDestTracingFilter, null, allArguments, argumentTypes, methodInterceptResult);
        clientDestTracingFilterInterceptor.afterMethod(enhancedClientDestTracingFilter, null, allArguments, argumentTypes, result);

        clientTracingFilterInterceptor.afterMethod(enhancedInstance, null, allArguments, argumentTypes, result);

        assertTraceSegments(segmentStorage.getTraceSegments());
    }

    @Test
    public void testClientWithException() throws Throwable {
        clientTracingFilterInterceptor.beforeMethod(enhancedInstance, null, allArguments, argumentTypes, methodInterceptResult);
        result.setValue(new Object());
        rpcInterceptor.onConstruct(null, new Object[]{rpc});
        clientDestTracingFilterInterceptor.onConstruct(enhancedClientDestTracingFilter, new Object[]{serverAddr});
        clientDestTracingFilterInterceptor.beforeMethod(enhancedClientDestTracingFilter, null, allArguments, argumentTypes, methodInterceptResult);
        clientDestTracingFilterInterceptor.afterMethod(enhancedClientDestTracingFilter, null, allArguments, argumentTypes, result);

        clientTracingFilterInterceptor.handleMethodException(enhancedInstance, null, allArguments, argumentTypes, new RuntimeException());
        clientTracingFilterInterceptor.afterMethod(enhancedInstance, null, allArguments, argumentTypes, result);

        assertTraceSegmentsWithError(segmentStorage.getTraceSegments());
    }

    @Test
    public void testClientWithResultHasException() throws Throwable {

        clientTracingFilterInterceptor.beforeMethod(enhancedInstance, null, allArguments, argumentTypes, methodInterceptResult);
        result.setException(new RuntimeException());
        rpcInterceptor.onConstruct(null, new Object[]{rpc});
        clientDestTracingFilterInterceptor.onConstruct(enhancedClientDestTracingFilter, new Object[]{serverAddr});
        clientDestTracingFilterInterceptor.beforeMethod(enhancedClientDestTracingFilter, null, allArguments, argumentTypes, methodInterceptResult);
        clientDestTracingFilterInterceptor.afterMethod(enhancedClientDestTracingFilter, null, allArguments, argumentTypes, result);

        clientTracingFilterInterceptor.afterMethod(enhancedInstance, null, allArguments, argumentTypes, result);

        assertTraceSegments(segmentStorage.getTraceSegments());
    }
}
