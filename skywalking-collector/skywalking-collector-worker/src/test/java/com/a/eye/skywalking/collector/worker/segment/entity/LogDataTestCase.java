package com.a.eye.skywalking.collector.worker.segment.entity;

import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author pengys5
 */
public class LogDataTestCase {

    @Test
    public void deserialize() throws IOException {
        LogData logData = new LogData();

        JsonReader reader = new JsonReader(new StringReader("{\"tm\":1, \"fi\": {\"test1\":\"test1\",\"test2\":\"test2\"}, \"skip\":\"skip\"}"));
        logData.deserialize(reader);

        Assert.assertEquals(1L, logData.getTime());

        Map<String, String> fields = logData.getFields();
        Assert.assertEquals("test1", fields.get("test1"));
        Assert.assertEquals("test2", fields.get("test2"));
        Assert.assertEquals(false, fields.containsKey("skip"));
    }
}
