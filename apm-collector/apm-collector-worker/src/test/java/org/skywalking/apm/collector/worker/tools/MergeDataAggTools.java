package org.skywalking.apm.collector.worker.tools;

import org.junit.Assert;
import org.skywalking.apm.collector.actor.AbstractClusterWorker;
import org.skywalking.apm.collector.worker.Const;
import org.skywalking.apm.collector.worker.mock.MergeDataAnswer;
import org.skywalking.apm.collector.worker.storage.JoinAndSplitData;

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
