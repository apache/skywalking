package org.skywalking.apm.collector.worker.httpserver;

import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.Writer;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.worker.segment.mock.SegmentMock;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({TestAbstractPost.class})
@PowerMockIgnore({"javax.management.*"})
public class AbstractPostTestCase {

    private TestAbstractPost post;

    @Before
    public void init() {
        ClusterWorkerContext clusterWorkerContext = PowerMockito.mock(ClusterWorkerContext.class);
        LocalWorkerContext localWorkerContext = PowerMockito.mock(LocalWorkerContext.class);
        post = spy(new TestAbstractPost(TestAbstractPost.WorkerRole.INSTANCE, clusterWorkerContext, localWorkerContext));
    }

    @Test
    public void testOnWork() throws Exception {
        String request = "testOnWork";
        post.onWork(request);
        verify(post).onReceive(anyString());
    }

    @Test
    public void testOnWorkError() throws Exception {
        post.onWork(new JsonObject());
        PowerMockito.verifyPrivate(post).invoke("saveException", any(IllegalArgumentException.class));
    }

    @Test
    public void testPostWithHttpServlet() throws Exception {
        SegmentMock segmentMock = new SegmentMock();

//        BufferedReader reader = new BufferedReader(new StringReader(segmentMock.mockCacheServiceExceptionSegmentAsString()));
        BufferedReader reader = new BufferedReader(new StringReader(segmentMock.mockCacheServiceSegmentAsString()));

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getReader()).thenReturn(reader);

        Writer writer = mock(Writer.class);
        PrintWriter printWriter = new PrintWriter(writer);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(printWriter);

        AbstractPost.PostWithHttpServlet servlet = new AbstractPost.PostWithHttpServlet(null);
        servlet.doPost(request, response);
    }
}
