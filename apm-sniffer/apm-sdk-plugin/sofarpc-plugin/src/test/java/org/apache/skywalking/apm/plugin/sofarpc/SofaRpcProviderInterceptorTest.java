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

package org.apache.skywalking.apm.plugin.sofarpc;

import com.alipay.sofa.rpc.client.ProviderInfo;
import com.alipay.sofa.rpc.context.RpcInternalContext;
import com.alipay.sofa.rpc.core.request.SofaRequest;
import com.alipay.sofa.rpc.core.response.SofaResponse;
import com.alipay.sofa.rpc.filter.ProviderInvoker;
import java.util.List;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.context.SW8CarrierItem;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegmentRef;
import org.apache.skywalking.apm.agent.core.context.util.TagValuePair;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.helper.SegmentRefHelper;
import org.apache.skywalking.apm.agent.test.helper.SpanHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.hamcrest.CoreMatchers;
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
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
@PrepareForTest({
    RpcInternalContext.class,
    SofaRequest.class,
    SofaResponse.class
})
public class SofaRpcProviderInterceptorTest {

    public static final String SKYWALKING_PREFIX = "skywalking.";

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule agentServiceRule = new AgentServiceRule();

    @Mock
    private EnhancedInstance enhancedInstance;

    private SofaRpcProviderInterceptor sofaRpcProviderInterceptor;

    @Mock
    private RpcInternalContext rpcContext;

    @Mock
    private ProviderInvoker invoker;

    private SofaRequest sofaRequest = PowerMockito.mock(SofaRequest.class);
    @Mock
    private MethodInterceptResult methodInterceptResult;

    private SofaResponse sofaResponse = PowerMockito.mock(SofaResponse.class);

    private Object[] allArguments;
    private Class[] argumentTypes;

    @Before
    public void setUp() throws Exception {
        sofaRpcProviderInterceptor = new SofaRpcProviderInterceptor();

        PowerMockito.mockStatic(RpcInternalContext.class);

        when(sofaRequest.getMethodName()).thenReturn("test");
        when(sofaRequest.getMethodArgSigs()).thenReturn(new String[] {"String"});
        when(sofaRequest.getMethodArgs()).thenReturn(new Object[] {"abc"});
        when(sofaRequest.getInterfaceName()).thenReturn("org.apache.skywalking.apm.test.TestSofaRpcService");
        PowerMockito.when(RpcInternalContext.getContext()).thenReturn(rpcContext);
        when(rpcContext.isConsumerSide()).thenReturn(false);
        final ProviderInfo providerInfo = new ProviderInfo();
        providerInfo.setHost("127.0.0.1");
        providerInfo.setPort(12200);
        when(rpcContext.getProviderInfo()).thenReturn(providerInfo);
        allArguments = new Object[] {sofaRequest};
        argumentTypes = new Class[] {sofaRequest.getClass()};
        Config.Agent.SERVICE_NAME = "SOFARPC-TestCases-APP";
    }

    @Test
    public void testProviderWithAttachment() throws Throwable {
        when(rpcContext.isConsumerSide()).thenReturn(false);
        when(sofaRequest.getRequestProp(SKYWALKING_PREFIX + SW8CarrierItem.HEADER_NAME)).thenReturn(
            "1-My40LjU=-MS4yLjM=-3-c2VydmljZQ==-aW5zdGFuY2U=-L2FwcA==-MTI3LjAuMC4xOjgwODA=");

        sofaRpcProviderInterceptor.beforeMethod(
            enhancedInstance, null, allArguments, argumentTypes, methodInterceptResult);
        sofaRpcProviderInterceptor.afterMethod(enhancedInstance, null, allArguments, argumentTypes, sofaResponse);
        assertProvider();
    }

    private void assertProvider() {
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        assertThat(SegmentHelper.getSpans(traceSegment).size(), is(1));
        assertProviderSpan(SegmentHelper.getSpans(traceSegment).get(0));
        assertTraceSegmentRef(traceSegment.getRef());
    }

    private void assertTraceSegmentRef(TraceSegmentRef actual) {
        assertThat(SegmentRefHelper.getSpanId(actual), is(3));
        assertThat(SegmentRefHelper.getParentServiceInstance(actual), is("instance"));
        assertThat(SegmentRefHelper.getTraceSegmentId(actual).toString(), is("3.4.5"));
    }

    private void assertProviderSpan(AbstractTracingSpan span) {
        assertCommonsAttribute(span);
        assertTrue(span.isEntry());
    }

    private void assertCommonsAttribute(AbstractTracingSpan span) {
        List<TagValuePair> tags = SpanHelper.getTags(span);
        assertThat(tags.size(), is(0));
        assertThat(SpanHelper.getLayer(span), CoreMatchers.is(SpanLayer.RPC_FRAMEWORK));
        assertThat(SpanHelper.getComponentId(span), is(43));
    }
}
