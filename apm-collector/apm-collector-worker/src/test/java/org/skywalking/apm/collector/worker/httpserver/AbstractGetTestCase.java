package org.skywalking.apm.collector.worker.httpserver;

import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * @author pengys5
 */
public class AbstractGetTestCase {

    private TestAbstractGet get = spy(new TestAbstractGet(TestAbstractGet.WorkerRole.INSTANCE, null, null));

    @Test
    public void testOnWork() throws Exception {
        Map<String, String[]> parameterMap = new HashMap<>();
        JsonObject response = new JsonObject();
        get.onWork(parameterMap, response);
        verify(get).onSearch(any(Map.class), any(JsonObject.class));
    }

    @Test
    public void testOnWorkError() throws Exception {
        Map<String, String[]> parameterMap = new HashMap<>();
        JsonObject response = new JsonObject();

        doThrow(new Exception("testOnWorkError")).when(get).onSearch(any(Map.class), any(JsonObject.class));
        get.onWork(parameterMap, response);

        Assert.assertEquals(false, response.get("isSuccess").getAsBoolean());
        Assert.assertEquals("testOnWorkError", response.get("reason").getAsString());
    }
}
