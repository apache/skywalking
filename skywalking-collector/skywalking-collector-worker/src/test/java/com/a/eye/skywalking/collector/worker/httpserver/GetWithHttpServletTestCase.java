package com.a.eye.skywalking.collector.worker.httpserver;

import com.a.eye.skywalking.collector.actor.LocalSyncWorkerRef;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

import static org.mockito.Mockito.*;

/**
 * @author pengys5
 */
public class GetWithHttpServletTestCase {

    @Test
    public void testDoGet() throws IOException, ServletException {
        LocalSyncWorkerRef workerRef = mock(LocalSyncWorkerRef.class);
        AbstractGet.GetWithHttpServlet servlet = new AbstractGet.GetWithHttpServlet(workerRef);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Integer status = (Integer) invocation.getArguments()[0];
                System.out.println(status);
                Assert.assertEquals(new Integer(200), status);
                return null;
            }
        }).when(response).setStatus(anyInt());

        PrintWriter writer = mock(PrintWriter.class);
        when(response.getWriter()).thenReturn(writer);

        servlet.doGet(request, response);
    }
}
