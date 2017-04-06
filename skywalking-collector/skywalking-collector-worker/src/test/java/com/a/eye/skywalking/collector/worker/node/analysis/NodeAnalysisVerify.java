package com.a.eye.skywalking.collector.worker.node.analysis;

import com.a.eye.skywalking.collector.worker.storage.RecordData;
import com.a.eye.skywalking.collector.worker.tools.RecordDataTool;
import org.junit.Assert;

import java.util.List;

/**
 * @author pengys5
 */
public enum NodeAnalysisVerify {
    INSTANCE;

    public void verify(List<RecordData> recordDataList, long timeSlice) {
        Assert.assertEquals(9, recordDataList.size());

        RecordData data_1 = RecordDataTool.INSTANCE.getRecord(recordDataList, timeSlice + "..-..[localhost:-1]");
        Assert.assertEquals("H2", data_1.getRecord().get("component").getAsString());
        Assert.assertEquals("[localhost:-1]", data_1.getRecord().get("peers").getAsString());
        Assert.assertEquals("[localhost:-1]", data_1.getRecord().get("aggId").getAsString());
        Assert.assertEquals(timeSlice, data_1.getRecord().get("timeSlice").getAsLong());

        RecordData data_2 = RecordDataTool.INSTANCE.getRecord(recordDataList, timeSlice + "..-..portal-service");
        Assert.assertEquals("Tomcat", data_2.getRecord().get("component").getAsString());
        Assert.assertEquals("portal-service", data_2.getRecord().get("peers").getAsString());
        Assert.assertEquals("portal-service", data_2.getRecord().get("aggId").getAsString());
        Assert.assertEquals(timeSlice, data_2.getRecord().get("timeSlice").getAsLong());

        RecordData data_3 = RecordDataTool.INSTANCE.getRecord(recordDataList, timeSlice + "..-..persistence-service");
        Assert.assertEquals(false, data_3.getRecord().has("component"));
        Assert.assertEquals("persistence-service", data_3.getRecord().get("peers").getAsString());
        Assert.assertEquals("persistence-service", data_3.getRecord().get("aggId").getAsString());
        Assert.assertEquals(timeSlice, data_3.getRecord().get("timeSlice").getAsLong());

        RecordData data_4 = RecordDataTool.INSTANCE.getRecord(recordDataList, timeSlice + "..-..[10.128.35.80:20880]");
        Assert.assertEquals("HttpClient", data_4.getRecord().get("component").getAsString());
        Assert.assertEquals("[10.128.35.80:20880]", data_4.getRecord().get("peers").getAsString());
        Assert.assertEquals("[10.128.35.80:20880]", data_4.getRecord().get("aggId").getAsString());
        Assert.assertEquals(timeSlice, data_4.getRecord().get("timeSlice").getAsLong());

        RecordData data_5 = RecordDataTool.INSTANCE.getRecord(recordDataList, timeSlice + "..-..[127.0.0.1:6379]");
        Assert.assertEquals("Redis", data_5.getRecord().get("component").getAsString());
        Assert.assertEquals("[127.0.0.1:6379]", data_5.getRecord().get("peers").getAsString());
        Assert.assertEquals("[127.0.0.1:6379]", data_5.getRecord().get("aggId").getAsString());
        Assert.assertEquals(timeSlice, data_5.getRecord().get("timeSlice").getAsLong());

        RecordData data_6 = RecordDataTool.INSTANCE.getRecord(recordDataList, timeSlice + "..-..[127.0.0.1:8002]");
        Assert.assertEquals("Motan", data_6.getRecord().get("component").getAsString());
        Assert.assertEquals("[127.0.0.1:8002]", data_6.getRecord().get("peers").getAsString());
        Assert.assertEquals("[127.0.0.1:8002]", data_6.getRecord().get("aggId").getAsString());
        Assert.assertEquals(timeSlice, data_6.getRecord().get("timeSlice").getAsLong());

        RecordData data_7 = RecordDataTool.INSTANCE.getRecord(recordDataList, timeSlice + "..-..User");
        Assert.assertEquals(false, data_7.getRecord().has("component"));
        Assert.assertEquals("User", data_7.getRecord().get("peers").getAsString());
        Assert.assertEquals("User", data_7.getRecord().get("aggId").getAsString());
        Assert.assertEquals(timeSlice, data_7.getRecord().get("timeSlice").getAsLong());

        RecordData data_8 = RecordDataTool.INSTANCE.getRecord(recordDataList, timeSlice + "..-..[127.0.0.1:3307]");
        Assert.assertEquals("Mysql", data_8.getRecord().get("component").getAsString());
        Assert.assertEquals("[127.0.0.1:3307]", data_8.getRecord().get("peers").getAsString());
        Assert.assertEquals("[127.0.0.1:3307]", data_8.getRecord().get("aggId").getAsString());
        Assert.assertEquals(timeSlice, data_8.getRecord().get("timeSlice").getAsLong());

        RecordData data_9 = RecordDataTool.INSTANCE.getRecord(recordDataList, timeSlice + "..-..cache-service");
        Assert.assertEquals(false, data_9.getRecord().has("component"));
        Assert.assertEquals("cache-service", data_9.getRecord().get("peers").getAsString());
        Assert.assertEquals("cache-service", data_9.getRecord().get("aggId").getAsString());
        Assert.assertEquals(timeSlice, data_9.getRecord().get("timeSlice").getAsLong());
    }
}
