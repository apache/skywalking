package org.skywalking.apm.collector.worker.httpserver;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.skywalking.apm.collector.actor.LocalSyncWorkerRef;
import org.skywalking.apm.collector.worker.segment.entity.Segment;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author pengys5
 */
public class PostWithHttpServletTestCase {

    private LocalSyncWorkerRef workerRef;
    private AbstractPost.PostWithHttpServlet servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private PrintWriter writer;

    @Before
    public void init() throws Exception {
        workerRef = mock(LocalSyncWorkerRef.class);
        servlet = new AbstractPost.PostWithHttpServlet(workerRef);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);

        writer = mock(PrintWriter.class);
        when(response.getWriter()).thenReturn(writer);
    }

    @Test
    public void testDoPost() throws Exception {

        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Integer status = (Integer)invocation.getArguments()[0];
                Assert.assertEquals(new Integer(200), status);
                return null;
            }
        }).when(response).setStatus(anyInt());

        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Segment segment = (Segment)invocation.getArguments()[0];
                Assert.assertEquals("TestTest2", segment.getTraceSegmentId());
                return null;
            }
        }).when(workerRef).tell(any(Segment.class));

        BufferedReader bufferedReader = new BufferedReader(new StringReader("[{\"ts\":\"TestTest2\"}]"));

        when(request.getReader()).thenReturn(bufferedReader);

        servlet.doPost(request, response);
    }

    @Test
    public void testDoPostError() throws Exception {
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Integer status = (Integer)invocation.getArguments()[0];
                Assert.assertEquals(new Integer(500), status);
                return null;
            }
        }).when(response).setStatus(anyInt());
        doThrow(new Exception()).when(workerRef).tell(anyString());
        servlet.doPost(request, response);
    }
}
