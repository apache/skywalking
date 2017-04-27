package com.a.eye.skywalking.collector.worker.tools;

import com.a.eye.skywalking.collector.actor.AbstractClusterWorker;
import com.a.eye.skywalking.collector.worker.Const;
import com.a.eye.skywalking.collector.worker.mock.MergeDataAnswer;
import com.a.eye.skywalking.collector.worker.storage.JoinAndSplitData;
import org.junit.Assert;

/**
 * @author pengys5
 */
public enum MergeDataAggTools {
    INSTANCE;

    public void testOnWork(AbstractClusterWorker agg, MergeDataAnswer mergeDataAnswer) throws Exception {
        String id = "2016" + Const.ID_SPLIT + "A" + Const.ID_SPLIT + "B";
        JoinAndSplitData joinAndSplitData = new JoinAndSplitData(id);
        joinAndSplitData.set("Column", "VALUE");
        agg.allocateJob(joinAndSplitData);
        Assert.assertEquals("VALUE", mergeDataAnswer.getJoinAndSplitDataList().get(0).asMap().get("Column"));
    }
}
