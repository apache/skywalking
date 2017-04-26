package com.a.eye.skywalking.collector.worker.tools;

import com.a.eye.skywalking.collector.actor.AbstractClusterWorker;
import com.a.eye.skywalking.collector.worker.Const;
import com.a.eye.skywalking.collector.worker.mock.RecordDataAnswer;
import com.a.eye.skywalking.collector.worker.storage.RecordData;
import org.junit.Assert;

/**
 * @author pengys5
 */
public enum RecordDataAggTools {
    INSTANCE;

    public void testOnWork(AbstractClusterWorker agg, RecordDataAnswer recordDataAnswer) throws Exception {
        String id = "2016" + Const.ID_SPLIT + "A" + Const.ID_SPLIT + "B";
        RecordData recordData = new RecordData(id);
        agg.allocateJob(recordData);
        RecordData result = RecordDataTool.INSTANCE.getRecord(recordDataAnswer.getRecordDataList(), id);
        Assert.assertEquals("A" + Const.ID_SPLIT + "B", result.get().get("aggId").getAsString());
    }
}
