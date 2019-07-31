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

package org.apache.skywalking.apm.plugin.hessian.v4;

import com.caucho.hessian.client.HessianConnection;
import com.caucho.hessian.client.HessianProxy;
import com.caucho.hessian.client.HessianURLConnection;
import java.net.URL;
import java.util.List;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.plugin.hessian.v4.define.HessianEnhanceCache;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import static org.hamcrest.CoreMatchers.is;

/**
 * @author Alan Lau
 */

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
@PrepareForTest(HessianProxy.class)
public class HessianProxySendRequestInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule agentServiceRule = new AgentServiceRule();

    @Mock
    private HessianProxy hessianProxy;

    @Mock
    private String methodName;


    private Class object = new Object().getClass();

    private HessianProxySendRequestInterceptor interceptor;

    private HessianConnection hessianConnection;

    @Mock
    private MethodInterceptResult methodInterceptResult;

    private Object[] allArguments;
    private Class[] argumentTypes;

    private EnhancedInstance enhancedInstance = new EnhancedInstance() {

        private Object object;

        @Override
        public Object getSkyWalkingDynamicField() {
            return object;
        }

        @Override public void setSkyWalkingDynamicField(Object value) {
            this.object = value;
        }
    };

    @Before
    public void setUp() throws Exception {
        interceptor = new HessianProxySendRequestInterceptor();
        PowerMockito.when(hessianProxy.getURL()).thenReturn(new URL("http://127.0.0.1:8080/TestHessian"));
        HessianEnhanceCache cache = new HessianEnhanceCache();
        cache.setUrl(hessianProxy.getURL());
        cache.setObj(object);
        enhancedInstance.setSkyWalkingDynamicField(cache);

        HessianConnection huc = PowerMockito.mock(HessianURLConnection.class);

        PowerMockito.when(huc.getStatusCode()).thenReturn(200);

        allArguments = new Object[] {huc};
        argumentTypes = new Class[] {HessianConnection.class};
    }

    @Test
    public void testSendRequest() throws Throwable {
        interceptor.beforeMethod(enhancedInstance, null, allArguments, argumentTypes, methodInterceptResult);
        interceptor.afterMethod(enhancedInstance, null, allArguments, argumentTypes, methodInterceptResult);

        Assert.assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        Assert.assertThat(spans.size(), is(1));
    }
}
