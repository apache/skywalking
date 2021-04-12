package org.apache.skywalking.apm.plugin.jsonrpc4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlecode.jsonrpc4j.JsonRpcHttpClient;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.context.util.TagValuePair;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.helper.SpanHelper;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class JsonRpcHttpClientTests {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;
    private MockJsonRpcHttpClient enhancedInstance;
    private ObjectMapper objectMapper = new ObjectMapper();
    private Object[] allArguments;
    private Class[] argumentTypes;
    @Mock
    private EnhancedInstance callBackEnhanceInstance;

    private JsonRpcHttpClientInterceptor httpClientInterceptor;
    private JsonRpcHttpClientPrepareConnectionInterceptor jsonRpcHttpClientPrepareConnectionInterceptor;
    private URL url;
    private HttpURLConnection httpURLConnection;

    @Before
    public void setUp() throws Exception {
        url = new URL("http://localhost:8080/test");
        enhancedInstance = new MockJsonRpcHttpClient(objectMapper, url, new HashMap<>(), false, false);
        allArguments = new Object[]{
                "OperationKey",
                "OperationValue"
        };
        argumentTypes = new Class[]{
                String.class,
                String.class
        };

        httpClientInterceptor = new JsonRpcHttpClientInterceptor();
        jsonRpcHttpClientPrepareConnectionInterceptor = new JsonRpcHttpClientPrepareConnectionInterceptor();
        allArguments = new Object[]{callBackEnhanceInstance};
        httpURLConnection = (HttpURLConnection) url.openConnection();
    }

    @Test
    public void testMethodAround() throws Throwable {
        Object[] objects = new Object[]{"OperationKey", url};
        httpClientInterceptor.onConstruct(enhancedInstance, objects);
        httpClientInterceptor.beforeMethod(enhancedInstance, null, objects, null, null);
        httpClientInterceptor.afterMethod(enhancedInstance, null, objects, null, null);
        jsonRpcHttpClientPrepareConnectionInterceptor.afterMethod(enhancedInstance, null, null, null, httpURLConnection);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        Assert.assertEquals(1, SegmentHelper.getSpans(traceSegment).size());
        AbstractTracingSpan finishedSpan = SegmentHelper.getSpans(traceSegment).get(0);

        List<TagValuePair> tags = SpanHelper.getTags(finishedSpan);
        assertThat(tags.size(), is(2));
        assertThat(tags.get(0).getValue(), is("GET"));
        assertThat(tags.get(1).getValue(), is(url.toString()));
        Assert.assertEquals(false, SpanHelper.getErrorOccurred(finishedSpan));
    }

    private class MockJsonRpcHttpClient extends JsonRpcHttpClient implements EnhancedInstance {

        private Object object;

        public MockJsonRpcHttpClient(ObjectMapper mapper, URL serviceUrl, Map<String, String> headers, boolean gzipRequests, boolean acceptGzipResponses) {
            super(mapper, serviceUrl, headers, gzipRequests, acceptGzipResponses);
        }

        @Override
        public Object getSkyWalkingDynamicField() {
            return object;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
            object = value;
        }
    }
}
