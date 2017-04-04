package com.a.eye.skywalking.collector.worker.node.analysis;

import com.a.eye.skywalking.collector.worker.storage.RecordData;
import org.junit.Assert;

import java.util.Map;

/**
 * @author pengys5
 */
public enum NodeAnalysisVerfiy {
    INSTANCE;

    public void verfiyCacheService(Map<String, RecordData> recordDataMap, long timeSlice) {
        RecordData data_1 = recordDataMap.get(timeSlice + "..-..[localhost:-1]");
        Assert.assertEquals("H2", data_1.getRecord().get("component").getAsString());
        Assert.assertEquals(false, data_1.getRecord().get("isReal").getAsBoolean());
        Assert.assertEquals("[localhost:-1]", data_1.getRecord().get("code").getAsString());
        Assert.assertEquals(timeSlice, data_1.getRecord().get("timeSlice").getAsLong());
        Assert.assertEquals("[localhost:-1]", data_1.getRecord().get("nickName").getAsString());

        RecordData data_2 = recordDataMap.get(timeSlice + "..-..[127.0.0.1:6379]");
        Assert.assertEquals("Redis", data_2.getRecord().get("component").getAsString());
        Assert.assertEquals(false, data_2.getRecord().get("isReal").getAsBoolean());
        Assert.assertEquals("[127.0.0.1:6379]", data_2.getRecord().get("code").getAsString());
        Assert.assertEquals(timeSlice, data_2.getRecord().get("timeSlice").getAsLong());
        Assert.assertEquals("[127.0.0.1:6379]", data_2.getRecord().get("nickName").getAsString());

        RecordData data_3 = recordDataMap.get(timeSlice + "..-..cache-service");
        Assert.assertEquals("Motan", data_3.getRecord().get("component").getAsString());
        Assert.assertEquals(true, data_3.getRecord().get("isReal").getAsBoolean());
        Assert.assertEquals("cache-service", data_3.getRecord().get("code").getAsString());
        Assert.assertEquals(timeSlice, data_3.getRecord().get("timeSlice").getAsLong());
        Assert.assertEquals("[127.0.0.1:8002]", data_3.getRecord().get("nickName").getAsString());
    }

    public void verfiyPortalService(Map<String, RecordData> recordDataMap, long timeSlice) {
        RecordData data_1 = recordDataMap.get(timeSlice + "..-..portal-service");
        Assert.assertEquals("Tomcat", data_1.getRecord().get("component").getAsString());
        Assert.assertEquals(true, data_1.getRecord().get("isReal").getAsBoolean());
        Assert.assertEquals("portal-service", data_1.getRecord().get("code").getAsString());
        Assert.assertEquals(timeSlice, data_1.getRecord().get("timeSlice").getAsLong());
        Assert.assertEquals("portal-service", data_1.getRecord().get("nickName").getAsString());

        RecordData data_2 = recordDataMap.get(timeSlice + "..-..[10.128.35.80:20880]");
        Assert.assertEquals("HttpClient", data_2.getRecord().get("component").getAsString());
        Assert.assertEquals(false, data_2.getRecord().get("isReal").getAsBoolean());
        Assert.assertEquals("[10.128.35.80:20880]", data_2.getRecord().get("code").getAsString());
        Assert.assertEquals(timeSlice, data_2.getRecord().get("timeSlice").getAsLong());
        Assert.assertEquals("[10.128.35.80:20880]", data_2.getRecord().get("nickName").getAsString());

        RecordData data_3 = recordDataMap.get(timeSlice + "..-..[127.0.0.1:8002]");
        Assert.assertEquals("Motan", data_3.getRecord().get("component").getAsString());
        Assert.assertEquals(false, data_3.getRecord().get("isReal").getAsBoolean());
        Assert.assertEquals("[127.0.0.1:8002]", data_3.getRecord().get("code").getAsString());
        Assert.assertEquals(timeSlice, data_3.getRecord().get("timeSlice").getAsLong());
        Assert.assertEquals("[127.0.0.1:8002]", data_3.getRecord().get("nickName").getAsString());

        RecordData data_4 = recordDataMap.get(timeSlice + "..-..User");
        Assert.assertEquals("User", data_4.getRecord().get("component").getAsString());
        Assert.assertEquals(false, data_4.getRecord().get("isReal").getAsBoolean());
    }

    public void verfiyPersistenceService(Map<String, RecordData> recordDataMap, long timeSlice) {
        RecordData data_1 = recordDataMap.get(timeSlice + "..-..persistence-service");
        Assert.assertEquals("Tomcat", data_1.getRecord().get("component").getAsString());
        Assert.assertEquals(true, data_1.getRecord().get("isReal").getAsBoolean());
        Assert.assertEquals("persistence-service", data_1.getRecord().get("code").getAsString());
        Assert.assertEquals(timeSlice, data_1.getRecord().get("timeSlice").getAsLong());
        Assert.assertEquals("[10.128.35.80:20880]", data_1.getRecord().get("nickName").getAsString());

        RecordData data_2 = recordDataMap.get(timeSlice + "..-..[127.0.0.1:3307]");
        Assert.assertEquals("Mysql", data_2.getRecord().get("component").getAsString());
        Assert.assertEquals(false, data_2.getRecord().get("isReal").getAsBoolean());
        Assert.assertEquals("[127.0.0.1:3307]", data_2.getRecord().get("code").getAsString());
        Assert.assertEquals(timeSlice, data_2.getRecord().get("timeSlice").getAsLong());
        Assert.assertEquals("[127.0.0.1:3307]", data_2.getRecord().get("nickName").getAsString());
    }
}
