package com.a.eye.skywalking.collector.worker.noderef.analysis;

import com.a.eye.skywalking.collector.worker.storage.RecordData;
import com.a.eye.skywalking.collector.worker.tools.RecordDataTool;
import org.junit.Assert;

import java.util.List;

/**
 * @author pengys5
 */
public enum NodeRefAnalysisVerify {
    INSTANCE;

    public void verify(List<RecordData> recordDataList, long timeSlice) {
        Assert.assertEquals(6, recordDataList.size());

        RecordData data_1 = RecordDataTool.INSTANCE.getRecord(recordDataList, timeSlice + "..-..portal-service..-..[10.128.35.80:20880]");
        Assert.assertEquals(true, data_1.getRecord().get("frontIsRealCode").getAsBoolean());
        Assert.assertEquals(false, data_1.getRecord().get("behindIsRealCode").getAsBoolean());
        Assert.assertEquals("portal-service", data_1.getRecord().get("front").getAsString());
        Assert.assertEquals("[10.128.35.80:20880]", data_1.getRecord().get("behind").getAsString());
        Assert.assertEquals("portal-service..-..[10.128.35.80:20880]", data_1.getRecord().get("aggId").getAsString());
        Assert.assertEquals(timeSlice, data_1.getRecord().get("timeSlice").getAsLong());

        RecordData data_2 = RecordDataTool.INSTANCE.getRecord(recordDataList, timeSlice + "..-..cache-service..-..[127.0.0.1:6379]");
        Assert.assertEquals(true, data_2.getRecord().get("frontIsRealCode").getAsBoolean());
        Assert.assertEquals(false, data_2.getRecord().get("behindIsRealCode").getAsBoolean());
        Assert.assertEquals("cache-service", data_2.getRecord().get("front").getAsString());
        Assert.assertEquals("[127.0.0.1:6379]", data_2.getRecord().get("behind").getAsString());
        Assert.assertEquals("cache-service..-..[127.0.0.1:6379]", data_2.getRecord().get("aggId").getAsString());
        Assert.assertEquals(timeSlice, data_2.getRecord().get("timeSlice").getAsLong());

        RecordData data_3 = RecordDataTool.INSTANCE.getRecord(recordDataList, timeSlice + "..-..cache-service..-..[localhost:-1]");
        Assert.assertEquals(true, data_3.getRecord().get("frontIsRealCode").getAsBoolean());
        Assert.assertEquals(false, data_3.getRecord().get("behindIsRealCode").getAsBoolean());
        Assert.assertEquals("cache-service", data_3.getRecord().get("front").getAsString());
        Assert.assertEquals("[localhost:-1]", data_3.getRecord().get("behind").getAsString());
        Assert.assertEquals("cache-service..-..[localhost:-1]", data_3.getRecord().get("aggId").getAsString());
        Assert.assertEquals(timeSlice, data_3.getRecord().get("timeSlice").getAsLong());

        RecordData data_4 = RecordDataTool.INSTANCE.getRecord(recordDataList, timeSlice + "..-..User..-..portal-service");
        Assert.assertEquals(true, data_4.getRecord().get("frontIsRealCode").getAsBoolean());
        Assert.assertEquals(true, data_4.getRecord().get("behindIsRealCode").getAsBoolean());
        Assert.assertEquals("User", data_4.getRecord().get("front").getAsString());
        Assert.assertEquals("portal-service", data_4.getRecord().get("behind").getAsString());
        Assert.assertEquals("User..-..portal-service", data_4.getRecord().get("aggId").getAsString());
        Assert.assertEquals(timeSlice, data_4.getRecord().get("timeSlice").getAsLong());

        RecordData data_5 = RecordDataTool.INSTANCE.getRecord(recordDataList, timeSlice + "..-..portal-service..-..[127.0.0.1:8002]");
        Assert.assertEquals(true, data_5.getRecord().get("frontIsRealCode").getAsBoolean());
        Assert.assertEquals(false, data_5.getRecord().get("behindIsRealCode").getAsBoolean());
        Assert.assertEquals("portal-service", data_5.getRecord().get("front").getAsString());
        Assert.assertEquals("[127.0.0.1:8002]", data_5.getRecord().get("behind").getAsString());
        Assert.assertEquals("portal-service..-..[127.0.0.1:8002]", data_5.getRecord().get("aggId").getAsString());
        Assert.assertEquals(timeSlice, data_5.getRecord().get("timeSlice").getAsLong());

        RecordData data_6 = RecordDataTool.INSTANCE.getRecord(recordDataList, timeSlice + "..-..persistence-service..-..[127.0.0.1:3307]");
        Assert.assertEquals(true, data_6.getRecord().get("frontIsRealCode").getAsBoolean());
        Assert.assertEquals(false, data_6.getRecord().get("behindIsRealCode").getAsBoolean());
        Assert.assertEquals("persistence-service", data_6.getRecord().get("front").getAsString());
        Assert.assertEquals("[127.0.0.1:3307]", data_6.getRecord().get("behind").getAsString());
        Assert.assertEquals("persistence-service..-..[127.0.0.1:3307]", data_6.getRecord().get("aggId").getAsString());
        Assert.assertEquals(timeSlice, data_6.getRecord().get("timeSlice").getAsLong());
    }
}
