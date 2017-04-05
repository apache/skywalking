package com.a.eye.skywalking.collector.worker.node.analysis;

import com.a.eye.skywalking.collector.worker.storage.RecordData;
import com.a.eye.skywalking.collector.worker.tools.RecordDataTool;
import org.junit.Assert;

import java.util.List;

/**
 * @author pengys5
 */
public enum NodeMappingAnalysisVerify {
    INSTANCE;

    public void verify(List<RecordData> recordDataList, long timeSlice) {
        Assert.assertEquals(2, recordDataList.size());

        RecordData data_1 = RecordDataTool.INSTANCE.getRecord(recordDataList, timeSlice + "..-..persistence-service..-..[10.128.35.80:20880]");
        Assert.assertEquals("persistence-service", data_1.getRecord().get("code").getAsString());
        Assert.assertEquals("[10.128.35.80:20880]", data_1.getRecord().get("peers").getAsString());
        Assert.assertEquals("persistence-service..-..[10.128.35.80:20880]", data_1.getRecord().get("aggId").getAsString());
        Assert.assertEquals(timeSlice, data_1.getRecord().get("timeSlice").getAsLong());

        RecordData data_2 = RecordDataTool.INSTANCE.getRecord(recordDataList, timeSlice + "..-..cache-service..-..[127.0.0.1:8002]");
        Assert.assertEquals("cache-service", data_2.getRecord().get("code").getAsString());
        Assert.assertEquals("[127.0.0.1:8002]", data_2.getRecord().get("peers").getAsString());
        Assert.assertEquals("cache-service..-..[127.0.0.1:8002]", data_2.getRecord().get("aggId").getAsString());
        Assert.assertEquals(timeSlice, data_2.getRecord().get("timeSlice").getAsLong());
    }
}
