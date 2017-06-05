package org.skywalking.apm.collector.worker.tools;

import org.junit.Assert;
import org.skywalking.apm.collector.actor.AbstractClusterWorker;
import org.skywalking.apm.collector.worker.Const;
import org.skywalking.apm.collector.worker.mock.RecordDataAnswer;
import org.skywalking.apm.collector.worker.storage.RecordData;

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
