package com.a.eye.skywalking.collector.worker.tools;

import com.a.eye.skywalking.collector.actor.AbstractClusterWorker;
import com.a.eye.skywalking.collector.worker.Const;
import com.a.eye.skywalking.collector.worker.mock.MetricDataAnswer;
import com.a.eye.skywalking.collector.worker.mock.RecordDataAnswer;
import com.a.eye.skywalking.collector.worker.storage.MetricData;
import com.a.eye.skywalking.collector.worker.storage.RecordData;
import org.junit.Assert;

/**
 * @author pengys5
 */
public enum MetricDataAggTools {
    INSTANCE;

    public void testOnWork(AbstractClusterWorker agg, MetricDataAnswer metricDataAnswer) throws Exception {
        String id = "2016" + Const.ID_SPLIT + "A" + Const.ID_SPLIT + "B";
        MetricData metricData = new MetricData(id);
        agg.allocateJob(metricData);
        Assert.assertEquals("A" + Const.ID_SPLIT + "B", metricDataAnswer.metricObj.get("aggId"));
    }
}
