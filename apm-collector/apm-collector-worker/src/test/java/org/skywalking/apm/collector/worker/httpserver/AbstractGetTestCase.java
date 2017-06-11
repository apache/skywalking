package org.skywalking.apm.collector.worker.httpserver;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author pengys5
 */
public class AbstractGetTestCase {

    private TestAbstractGet get = spy(new TestAbstractGet(TestAbstractGet.WorkerRole.INSTANCE, null, null));

    private HttpServletResponse response;
    private PrintWriter writer;

    @Before
    public void init() throws IOException {
        writer = mock(PrintWriter.class);
        response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(writer);
    }

    @Test
    public void testOnWork() throws Exception {
        Map<String, String[]> parameterMap = new HashMap<>();
        get.onWork(parameterMap, response);
        verify(get).onReceive(any(Map.class), any(JsonObject.class));
    }

    @Test
    public void testOnWorkError() throws Exception {
        Map<String, String[]> parameterMap = new HashMap<>();

        doAnswer(new Answer() {
            @Override public Object answer(InvocationOnMock invocation) throws Throwable {
                JsonObject response = (JsonObject)invocation.getArguments()[0];
                Assert.assertEquals(false, response.get("isSuccess").getAsBoolean());
                Assert.assertEquals("testOnWorkError", response.get("reason").getAsString());
                return null;
            }
        }).when(writer).print(any(JsonObject.class));

        doThrow(new Exception("testOnWorkError")).when(get).onReceive(any(Map.class), any(JsonObject.class));
        get.onWork(parameterMap, response);
    }
}
