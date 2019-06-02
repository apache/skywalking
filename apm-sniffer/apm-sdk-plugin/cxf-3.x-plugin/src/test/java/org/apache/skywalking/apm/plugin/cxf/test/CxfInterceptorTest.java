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

package org.apache.skywalking.apm.plugin.cxf.test;

import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.service.model.MessageInfo;
import org.apache.cxf.service.model.OperationInfo;
import org.apache.cxf.transport.http.Address;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.LogDataEntity;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.context.util.TagValuePair;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.helper.SpanHelper;
import org.apache.skywalking.apm.agent.test.tools.*;
import org.apache.skywalking.apm.plugin.cxf.CxfDynamicInterceptor;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import sun.net.www.protocol.http.HttpURLConnection;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class CxfInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule agentServiceRule = new AgentServiceRule();

    @Mock
    private EnhancedInstance enhancedInstance;

    private CxfDynamicInterceptor cxfDynamicInterceptor;

    private Address address;
    @Mock
    private MessageImpl messageImpl;
    @Mock
    private SoapMessage soapMessage;
    @Mock
    private HTTPClientPolicy httpClientPolicy;
    @Mock
    private OperationInfo operationInfo;
    @Mock
    private MessageInfo messageInfo;
    @Mock
    private MethodInterceptResult methodInterceptResult;
    @Mock
    private HttpURLConnection httpURLConnection;


    private Object[] allArguments;
    private Class[] argumentTypes;


    @Before
    public void setUp() throws Exception {


        Config.Agent.ACTIVE_V1_HEADER = true;
        cxfDynamicInterceptor = new CxfDynamicInterceptor();
        Config.Agent.SERVICE_NAME = "cxf-Consumer";
        when(messageImpl.get(Message.ENDPOINT_ADDRESS)).thenReturn("http://localhost:8083/test2/ws/api?wsdl");
        when(soapMessage.get(Message.ENDPOINT_ADDRESS)).thenReturn("http://localhost:8083/test2/ws/api");
        when(soapMessage.get("org.apache.cxf.service.model.MessageInfo")).thenReturn(messageInfo);
        when(messageInfo.getOperation()).thenReturn(operationInfo);

        when(operationInfo.getInputName()).thenReturn("remoteServiceMethod");

    }

    @After
    public void clear() {
        Config.Agent.ACTIVE_V1_HEADER = false;
    }

    /**
     * Test cxf for the first time to get wsdl information from remote
     *
     * @throws Throwable
     */
    @Test
    public void testConsumerGetWsdl() throws Throwable {
        address = new Address("http://localhost:8083/test2/ws/api?wsdl");
        allArguments = new Object[]{messageImpl, address, httpClientPolicy};
        argumentTypes = new Class[]{Message.class, Address.class, HTTPClientPolicy.class};

        cxfDynamicInterceptor.beforeMethod(enhancedInstance, null, allArguments, argumentTypes, methodInterceptResult);
        cxfDynamicInterceptor.afterMethod(enhancedInstance, null, allArguments, argumentTypes, httpURLConnection);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertConsumerWsdlSpan(spans.get(0));
    }
    /**
     * Test cxf for the first time to get wsdl happened exception
     *
     * @throws Throwable
     */
    @Test
    public void testConsumerGetWsdlWithException() throws Throwable {
        address = new Address("http://localhost:8083/test2/ws/api?wsdl");
        allArguments = new Object[]{messageImpl, address, httpClientPolicy};
        argumentTypes = new Class[]{Message.class, Address.class, HTTPClientPolicy.class};

        cxfDynamicInterceptor.beforeMethod(enhancedInstance, null, allArguments, argumentTypes, methodInterceptResult);
        cxfDynamicInterceptor.handleMethodException(enhancedInstance, null, allArguments, argumentTypes, new RuntimeException());
        cxfDynamicInterceptor.afterMethod(enhancedInstance, null, allArguments, argumentTypes, httpURLConnection);


        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertConsumerWsdlSpan(spans.get(0));

        List<LogDataEntity> logDataEntities = SpanHelper.getLogs(spans.get(0));
        MatcherAssert.assertThat(logDataEntities.size(), is(1));
        SpanAssert.assertException(logDataEntities.get(0), RuntimeException.class);
    }

    /**
     * After testing cxf to generate native code, the real call to the remote service
     *
     * @throws Throwable
     */
    @Test
    public void testConsumerRPC() throws Throwable {
        address = new Address("http://localhost:8083/test2/ws/api");
        allArguments = new Object[]{soapMessage, address, httpClientPolicy};
        argumentTypes = new Class[]{Message.class, Address.class, HTTPClientPolicy.class};

        cxfDynamicInterceptor.beforeMethod(enhancedInstance, null, allArguments, argumentTypes, methodInterceptResult);
        cxfDynamicInterceptor.afterMethod(enhancedInstance, null, allArguments, argumentTypes, httpURLConnection);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertConsumerRPCSpan(spans.get(0));
    }
    /**
     * Test cxf for the first time to get wsdl happened exception
     *
     * @throws Throwable
     */
    @Test
    public void testConsumerRPCWithException() throws Throwable {
        address = new Address("http://localhost:8083/test2/ws/api");
        allArguments = new Object[]{soapMessage, address, httpClientPolicy};
        argumentTypes = new Class[]{Message.class, Address.class, HTTPClientPolicy.class};

        cxfDynamicInterceptor.beforeMethod(enhancedInstance, null, allArguments, argumentTypes, methodInterceptResult);
        cxfDynamicInterceptor.handleMethodException(enhancedInstance, null, allArguments, argumentTypes, new RuntimeException());
        cxfDynamicInterceptor.afterMethod(enhancedInstance, null, allArguments, argumentTypes, httpURLConnection);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertConsumerRPCSpan(spans.get(0));

        List<LogDataEntity> logDataEntities = SpanHelper.getLogs(spans.get(0));
        MatcherAssert.assertThat(logDataEntities.size(), is(1));
        SpanAssert.assertException(logDataEntities.get(0), RuntimeException.class);
    }

    private void assertConsumerWsdlSpan(AbstractTracingSpan span) {
        List<TagValuePair> tags = SpanHelper.getTags(span);
        assertThat(tags.size(), is(1));
        assertThat(SpanHelper.getLayer(span), CoreMatchers.is(SpanLayer.HTTP));
        //assertThat(SpanHelper.getComponentId(span), is(63));
        assertThat(tags.get(0).getValue(), is("http://localhost:8083/test2/ws/api?wsdl"));
        assertThat(span.getOperationName(), is("get-remote-xxx?wsdl-info"));
        assertTrue(span.isExit());
    }

    private void assertConsumerRPCSpan(AbstractTracingSpan span) {
        List<TagValuePair> tags = SpanHelper.getTags(span);
        assertThat(tags.size(), is(1));
        assertThat(SpanHelper.getLayer(span), CoreMatchers.is(SpanLayer.RPC_FRAMEWORK));
        //assertThat(SpanHelper.getComponentId(span), is(63));
        assertThat(tags.get(0).getValue(), is("http://localhost:8083/test2/ws/api"));
        assertThat(span.getOperationName(), is("remoteServiceMethod"));
        assertTrue(span.isExit());
    }


}
