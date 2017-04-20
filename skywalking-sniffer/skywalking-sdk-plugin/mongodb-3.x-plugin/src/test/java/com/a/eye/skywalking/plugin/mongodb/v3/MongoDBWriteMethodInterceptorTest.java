package com.a.eye.skywalking.plugin.mongodb.v3;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.a.eye.skywalking.api.boot.ServiceManager;
import com.a.eye.skywalking.api.conf.Config;
import com.a.eye.skywalking.api.context.TracerContext;
import com.a.eye.skywalking.api.plugin.interceptor.EnhancedClassInstanceContext;
import com.a.eye.skywalking.api.plugin.interceptor.enhance.InstanceMethodInvokeContext;
import com.a.eye.skywalking.sniffer.mock.context.MockTracerContextListener;
import com.a.eye.skywalking.sniffer.mock.context.SegmentAssert;
import com.a.eye.skywalking.trace.LogData;
import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.TraceSegment;
import com.a.eye.skywalking.trace.tag.Tags;
import com.mongodb.bulk.DeleteRequest;
import com.mongodb.operation.DeleteOperation;

@RunWith(MockitoJUnitRunner.class)
public class MongoDBWriteMethodInterceptorTest {

    private MongoDBMethodInterceptor interceptor;
    private MockTracerContextListener mockTracerContextListener;

    @Mock
    private EnhancedClassInstanceContext classInstanceContext;
    @Mock
    private InstanceMethodInvokeContext methodInvokeContext;
    @Mock
    private DeleteOperation deleteOperation;

    @Before
    public void setUp() throws Exception {
        ServiceManager.INSTANCE.boot();

        interceptor = new MongoDBMethodInterceptor();
        mockTracerContextListener = new MockTracerContextListener();

        TracerContext.ListenerManager.add(mockTracerContextListener);

        Config.Plugin.MongoDB.TRACE_PARAM = true;

        when(classInstanceContext.get(MongoDBMethodInterceptor.MONGODB_HOST, String.class)).thenReturn("127.0.0.1");
        when(classInstanceContext.get(MongoDBMethodInterceptor.MONGODB_PORT)).thenReturn(27017);
        when(methodInvokeContext.methodName()).thenReturn("find");

        BsonDocument document = new BsonDocument();
        document.append("name", new BsonString("by"));

        List<DeleteRequest> requestList = new ArrayList<DeleteRequest>();

        DeleteRequest deleteRequest = new DeleteRequest(document);

        requestList.add(deleteRequest);

        when(deleteOperation.getDeleteRequests()).thenReturn(requestList);

        when(methodInvokeContext.allArguments()).thenReturn(new Object[] { deleteOperation });
    }

    @Test
    public void testIntercept() {
        interceptor.beforeMethod(classInstanceContext, methodInvokeContext, null);
        interceptor.afterMethod(classInstanceContext, methodInvokeContext, null);

        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                Span span = traceSegment.getSpans().get(0);
                assertRedisSpan(span);
            }
        });
    }

    private void assertRedisSpan(Span span) {
        // assertThat(span.getOperationName(), is("MongoDB/DeleteOperation"));
        assertThat(Tags.PEER_HOST.get(span), is("127.0.0.1"));
        assertThat(Tags.PEER_PORT.get(span), is(27017));
        assertThat(Tags.COMPONENT.get(span), is("MongoDB"));
        // assertThat(Tags.DB_STATEMENT.get(span), is("DeleteOperation { \"name\" : \"by\" },"));
        assertThat(Tags.DB_TYPE.get(span), is("MongoDB"));
        assertTrue(Tags.SPAN_LAYER.isDB(span));
    }

    @Test
    public void testInterceptWithException() {
        interceptor.beforeMethod(classInstanceContext, methodInvokeContext, null);
        interceptor.handleMethodException(new RuntimeException(), classInstanceContext, methodInvokeContext);
        interceptor.afterMethod(classInstanceContext, methodInvokeContext, null);

        mockTracerContextListener.assertSize(1);
        mockTracerContextListener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment traceSegment) {
                assertThat(traceSegment.getSpans().size(), is(1));
                Span span = traceSegment.getSpans().get(0);
                assertRedisSpan(span);
                assertThat(span.getLogs().size(), is(1));
                assertLogData(span.getLogs().get(0));
            }
        });
    }

    private void assertLogData(LogData logData) {
        MatcherAssert.assertThat(logData.getFields().size(), is(4));
        MatcherAssert.assertThat(logData.getFields().get("event"), CoreMatchers.<Object> is("error"));
        assertEquals(logData.getFields().get("error.kind"), RuntimeException.class.getName());
        assertNull(logData.getFields().get("message"));
    }

    @After
    public void tearDown() throws Exception {
        TracerContext.ListenerManager.remove(mockTracerContextListener);
    }

}
