package com.a.eye.skywalking.collector.worker.httpserver;

import com.google.gson.JsonObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

/**
 * @author pengys5
 */
public class AbstractGetTestCase {

    private TestAbstractGet get;

    @Before
    public void init() {
        get = mock(TestAbstractGet.class);
    }

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
