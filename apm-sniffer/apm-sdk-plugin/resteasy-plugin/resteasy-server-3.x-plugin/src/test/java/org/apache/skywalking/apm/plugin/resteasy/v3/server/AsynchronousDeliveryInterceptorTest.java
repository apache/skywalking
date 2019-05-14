package org.apache.skywalking.apm.plugin.resteasy.v3.server;

import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.LogDataEntity;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.helper.SpanHelper;
import org.apache.skywalking.apm.agent.test.tools.*;
import org.jboss.resteasy.core.ResourceInvoker;
import org.jboss.resteasy.specimpl.MultivaluedMapImpl;
import org.jboss.resteasy.specimpl.ResteasyHttpHeaders;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.HttpResponse;
import org.jboss.resteasy.spi.ResteasyAsynchronousContext;
import org.jboss.resteasy.spi.ResteasyUriInfo;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

/**
 * @author yan-fucheng
 */
@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class AsynchronousDeliveryInterceptorTest {

    private SynchronousDispatcherInterceptor synchronousDispatcherInterceptor;
    private SynchronousDispatcherExceptionInterceptor synchronousDispatcherExceptionInterceptor;

    private AsynchronousResponseInjectorInterceptor asynchronousResponseInjectorInterceptor;
    private AsynchronousDeliveryInterceptor asynchronousDeliveryInterceptor;
    private AsynchronousDeliveryExceptionInterceptor asynchronousDeliveryExceptionInterceptor;

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    @Mock
    HttpRequest request;

    @Mock
    HttpResponse response;

    @Mock
    ResourceInvoker resourceInvoker;

    @Mock
    private MethodInterceptResult methodInterceptResult;

    @Mock
    private ResteasyAsynchronousContext resteasyAsynchronousContext;

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

    private Object[] arguments;
    private Class[] argumentType;

    private Object[] exceptionArguments;
    private Class[] exceptionArgumentType;

    @Before
    public void setup() throws URISyntaxException {
        synchronousDispatcherInterceptor = new SynchronousDispatcherInterceptor();
        synchronousDispatcherExceptionInterceptor = new SynchronousDispatcherExceptionInterceptor();

        asynchronousResponseInjectorInterceptor = new AsynchronousResponseInjectorInterceptor();
        asynchronousDeliveryInterceptor = new AsynchronousDeliveryInterceptor();
        asynchronousDeliveryExceptionInterceptor = new AsynchronousDeliveryExceptionInterceptor();

        when(request.getUri()).thenReturn(new ResteasyUriInfo(new URI("http://localhost:8080/test/testRequestURL")));
        when(request.getHttpHeaders()).thenReturn(new ResteasyHttpHeaders(new MultivaluedMapImpl<String, String>()));
        when(response.getStatus()).thenReturn(200);
        when(request.getAsyncContext()).thenReturn(resteasyAsynchronousContext);
        when(request.getAsyncContext().isSuspended()).thenReturn(true);
        arguments = new Object[] {request, response, resourceInvoker};
        argumentType = new Class[] {request.getClass(), response.getClass(), resourceInvoker.getClass()};

        exceptionArguments = new Object[] {request, response, new RuntimeException()};
        exceptionArgumentType = new Class[] {request.getClass(), response.getClass(), new RuntimeException().getClass()};
    }

    @Test
    public void testWithoutSerializedContextData() throws Throwable {
        synchronousDispatcherInterceptor.beforeMethod(enhancedInstance, null, arguments, argumentType, methodInterceptResult);
        asynchronousResponseInjectorInterceptor.beforeMethod(enhancedInstance, null, new Object[] {request, response}, argumentType, methodInterceptResult);
        synchronousDispatcherInterceptor.afterMethod(enhancedInstance, null, arguments, argumentType, null);
        asynchronousDeliveryInterceptor.afterMethod(enhancedInstance, null, arguments, argumentType, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        AssertTools.assertHttpSpan(spans.get(0));
    }

    @Test
    public void testWithMainThreadOccurException() throws Throwable {
        synchronousDispatcherInterceptor.beforeMethod(enhancedInstance, null, arguments, argumentType, methodInterceptResult);
        asynchronousResponseInjectorInterceptor.beforeMethod(enhancedInstance, null, new Object[] {request, response}, argumentType, methodInterceptResult);
        asynchronousDeliveryExceptionInterceptor.beforeMethod(enhancedInstance, null, exceptionArguments, exceptionArgumentType, null);
        synchronousDispatcherExceptionInterceptor.beforeMethod(enhancedInstance, null, exceptionArguments, exceptionArgumentType, null);
        asynchronousDeliveryExceptionInterceptor.afterMethod(enhancedInstance, null, exceptionArguments, exceptionArgumentType, null);
        synchronousDispatcherInterceptor.afterMethod(enhancedInstance, null, arguments, argumentType, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);

        AssertTools.assertHttpSpan(spans.get(0));
        List<LogDataEntity> logDataEntities = SpanHelper.getLogs(spans.get(0));
        assertThat(logDataEntities.size(), is(1));
        SpanAssert.assertException(logDataEntities.get(0), RuntimeException.class);
    }

    @Test
    public void testWithAsyncThreadOccurException() throws Throwable {
        synchronousDispatcherInterceptor.beforeMethod(enhancedInstance, null, arguments, argumentType, methodInterceptResult);
        asynchronousResponseInjectorInterceptor.beforeMethod(enhancedInstance, null, new Object[] {request, response}, argumentType, methodInterceptResult);
        synchronousDispatcherInterceptor.afterMethod(enhancedInstance, null, arguments, argumentType, null);
        asynchronousDeliveryExceptionInterceptor.beforeMethod(enhancedInstance, null, exceptionArguments, exceptionArgumentType, null);
        synchronousDispatcherExceptionInterceptor.beforeMethod(enhancedInstance, null, exceptionArguments, exceptionArgumentType, null);
        asynchronousDeliveryExceptionInterceptor.afterMethod(enhancedInstance, null, exceptionArguments, exceptionArgumentType, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);

        AssertTools.assertHttpSpan(spans.get(0));
        List<LogDataEntity> logDataEntities = SpanHelper.getLogs(spans.get(0));
        assertThat(logDataEntities.size(), is(1));
        SpanAssert.assertException(logDataEntities.get(0), RuntimeException.class);
    }
}
