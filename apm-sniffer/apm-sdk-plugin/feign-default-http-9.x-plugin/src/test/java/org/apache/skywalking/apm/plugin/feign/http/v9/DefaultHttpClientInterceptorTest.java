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

package org.apache.skywalking.apm.plugin.feign.http.v9;

import feign.Request;
import feign.Response;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.LogDataEntity;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.context.util.TagValuePair;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.helper.SpanHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import java.nio.charset.Charset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static junit.framework.TestCase.assertNotNull;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
@PrepareForTest({Response.class})
public class DefaultHttpClientInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule agentServiceRule = new AgentServiceRule();

    private DefaultHttpClientInterceptor defaultHttpClientInterceptor;

    @Mock
    private EnhancedInstance enhancedInstance;

    @Mock
    private MethodInterceptResult result;

    private Request request;

    private Object[] allArguments;
    private Class[] argumentTypes;

    @Before
    public void setUp() throws Exception {
        PathVarInterceptor.URL_CONTEXT.set(new FeignResolvedURL("/test/{pathVar}", "/test/var"));
        Map<String, Collection<String>> headers = new LinkedHashMap<String, Collection<String>>();
        request = Request.create("GET", "http://skywalking.org/test/var", headers, "Test".getBytes(), Charset.forName("UTF-8"));
        Request.Options options = new Request.Options();
        allArguments = new Object[] {
            request,
            options
        };
        argumentTypes = new Class[] {
            request.getClass(),
            options.getClass()
        };
        defaultHttpClientInterceptor = new DefaultHttpClientInterceptor();

    }

    @Test
    public void testMethodsAround() throws Throwable {
        Response response = mock(Response.class);
        when(response.status()).thenReturn(200);
        defaultHttpClientInterceptor.beforeMethod(enhancedInstance, null, allArguments, argumentTypes, result);
        defaultHttpClientInterceptor.afterMethod(enhancedInstance, null, allArguments, argumentTypes, response);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);

        Assert.assertEquals(1, SegmentHelper.getSpans(traceSegment).size());
        AbstractTracingSpan finishedSpan = SegmentHelper.getSpans(traceSegment).get(0);
        assertSpan(finishedSpan);

        List<TagValuePair> tags = SpanHelper.getTags(finishedSpan);
        assertThat(tags.size(), is(2));
        assertThat(tags.get(0).getValue(), is("GET"));
        assertThat(tags.get(1).getValue(), is("http://skywalking.org/test/var"));
        assertThat(finishedSpan.getOperationName(), is("/test/{pathVar}"));

        Assert.assertEquals(false, SpanHelper.getErrorOccurred(finishedSpan));
    }

    @Test
    public void testMethodsAroundError() throws Throwable {
        defaultHttpClientInterceptor.beforeMethod(enhancedInstance, null, allArguments, argumentTypes, result);

        Response response = mock(Response.class);
        when(response.status()).thenReturn(404);
        defaultHttpClientInterceptor.afterMethod(enhancedInstance, null, allArguments, argumentTypes, response);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);

        Assert.assertEquals(1, SegmentHelper.getSpans(traceSegment).size());
        AbstractTracingSpan finishedSpan = SegmentHelper.getSpans(traceSegment).get(0);
        assertSpan(finishedSpan);

        List<TagValuePair> tags = SpanHelper.getTags(finishedSpan);
        assertThat(tags.size(), is(3));
        assertThat(tags.get(0).getValue(), is("GET"));
        assertThat(tags.get(1).getValue(), is("http://skywalking.org/test/var"));
        assertThat(tags.get(2).getValue(), is("404"));
        assertThat(finishedSpan.getOperationName(), is("/test/{pathVar}"));

        Assert.assertEquals(true, SpanHelper.getErrorOccurred(finishedSpan));
    }

    private void assertSpan(AbstractTracingSpan span) {
        assertThat(SpanHelper.getLayer(span), CoreMatchers.is(SpanLayer.HTTP));
        assertThat(SpanHelper.getComponentId(span), is(11));
    }

    @Test
    public void testException() throws Throwable {
        defaultHttpClientInterceptor.beforeMethod(enhancedInstance, null, allArguments, argumentTypes, result);

        defaultHttpClientInterceptor.handleMethodException(enhancedInstance, null, allArguments, argumentTypes, new NullPointerException("testException"));

        Response response = mock(Response.class);
        when(response.status()).thenReturn(200);
        defaultHttpClientInterceptor.afterMethod(enhancedInstance, null, allArguments, argumentTypes, response);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);

        Assert.assertEquals(1, SegmentHelper.getSpans(traceSegment).size());
        AbstractTracingSpan finishedSpan = SegmentHelper.getSpans(traceSegment).get(0);
        assertSpan(finishedSpan);

        List<TagValuePair> tags = SpanHelper.getTags(finishedSpan);
        assertThat(tags.size(), is(2));
        assertThat(tags.get(0).getValue(), is("GET"));
        assertThat(tags.get(1).getValue(), is("http://skywalking.org/test/var"));

        Assert.assertEquals(true, SpanHelper.getErrorOccurred(finishedSpan));

        Assert.assertEquals(1, SpanHelper.getLogs(finishedSpan).size());

        LogDataEntity logDataEntity = SpanHelper.getLogs(finishedSpan).get(0);
        assertThat(logDataEntity.getLogs().size(), is(4));
        assertThat(logDataEntity.getLogs().get(0).getValue(), CoreMatchers.<Object>is("error"));
        assertThat(logDataEntity.getLogs()
                                .get(1)
                                .getValue(), CoreMatchers.<Object>is(NullPointerException.class.getName()));
        assertThat(logDataEntity.getLogs().get(2).getValue(), is("testException"));
        assertNotNull(logDataEntity.getLogs().get(3).getValue());
    }

    @After
    public void clean() {
        PathVarInterceptor.URL_CONTEXT.remove();
    }
}
