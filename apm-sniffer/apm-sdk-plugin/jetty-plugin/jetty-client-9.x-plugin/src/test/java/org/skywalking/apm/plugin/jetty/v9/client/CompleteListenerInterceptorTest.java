package org.skywalking.apm.plugin.jetty.v9.client;

import java.net.URI;
import org.eclipse.jetty.client.HttpRequest;
import org.eclipse.jetty.client.HttpResponse;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.http.HttpFields;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.skywalking.apm.agent.core.context.ContextSnapshot;
import org.skywalking.apm.agent.core.context.SW3CarrierItem;
import org.skywalking.apm.agent.core.context.ids.DistributedTraceId;
import org.skywalking.apm.agent.core.context.ids.ID;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.skywalking.apm.agent.core.context.trace.TraceSegmentRef;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.test.helper.SegmentRefHelper;
import org.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.skywalking.apm.agent.test.tools.SegmentStorage;
import org.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.skywalking.apm.agent.test.tools.TracingSegmentRunner;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class CompleteListenerInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;
    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();
    @Mock
    private Result result;
    @Mock
    private HttpRequest httpRequest;
    @Mock
    private HttpResponse httpResponse;
    private Object[] allArguments;
    private Class[] argumentTypes;
    private CompleteListenerInterceptor interceptor;

    @Mock
    private ContextSnapshot contextSnapshot;

    private EnhancedInstance objectInstanceWithoutSnapshot = new EnhancedInstance() {
        @Override
        public Object getSkyWalkingDynamicField() {
            return null;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {

        }
    };

    private EnhancedInstance objectInstanceWithSnapshot = new EnhancedInstance() {
        @Override
        public Object getSkyWalkingDynamicField() {
            return contextSnapshot;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {

        }
    };

    @Before
    public void setUp() {
        interceptor = new CompleteListenerInterceptor();
        when(result.getResponse()).thenReturn(httpResponse);
        when(httpRequest.getURI()).thenReturn(URI.create("http://localhost:8080/test"));
        when(result.getRequest()).thenReturn(httpRequest);
        allArguments = new Object[] {result};
        argumentTypes = new Class[] {result.getClass()};
        when(contextSnapshot.isValid()).thenReturn(true);
        when(contextSnapshot.getEntryApplicationInstanceId()).thenReturn(1);
        when(contextSnapshot.getSpanId()).thenReturn(2);
        when(contextSnapshot.getTraceSegmentId()).thenReturn(mock(ID.class));
        when(contextSnapshot.getDistributedTraceId()).thenReturn(mock(DistributedTraceId.class));
        when(contextSnapshot.getEntryOperationName()).thenReturn("1");
        when(contextSnapshot.getParentOperationName()).thenReturn("2");
    }

    @Test
    public void testMethodAroundWithoutSnapshot() throws Throwable {
        interceptor.beforeMethod(objectInstanceWithoutSnapshot, null, allArguments, argumentTypes, null);
        interceptor.afterMethod(objectInstanceWithoutSnapshot, null, allArguments, argumentTypes, null);
        assertThat(segmentStorage.getTraceSegments().size(), is(0));
    }

    @Test
    public void testMethodAroundWithSnapshot() throws Throwable {
        HttpFields fields = new HttpFields();
        when(httpResponse.getHeaders()).thenReturn(fields);
        interceptor.beforeMethod(objectInstanceWithSnapshot, null, allArguments, argumentTypes, null);
        interceptor.afterMethod(objectInstanceWithSnapshot, null, allArguments, argumentTypes, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        assertThat(traceSegment.getRefs().size(), is(1));
    }

    @Test
    public void testMethodAroundWithSnapshotAndHeader() throws Throwable {
        HttpFields fields = new HttpFields();
        fields.put(SW3CarrierItem.HEADER_NAME, "1.234.111|3|1|1|#192.168.1.8:18002|#/portal/|#/testEntrySpan|#AQA*#AQA*Et0We0tQNQA*");
        when(httpResponse.getHeaders()).thenReturn(fields);
        interceptor.beforeMethod(objectInstanceWithSnapshot, null, allArguments, argumentTypes, null);
        interceptor.afterMethod(objectInstanceWithSnapshot, null, allArguments, argumentTypes, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        assertThat(traceSegment.getRefs().size(), is(1));
        TraceSegmentRef ref = traceSegment.getRefs().get(0);
        assertThat(SegmentRefHelper.getEntryApplicationInstanceId(ref), is(1));
        assertThat(SegmentRefHelper.getSpanId(ref), is(3));
        assertThat(SegmentRefHelper.getTraceSegmentId(ref).toString(), is("1.234.111"));
    }
}
