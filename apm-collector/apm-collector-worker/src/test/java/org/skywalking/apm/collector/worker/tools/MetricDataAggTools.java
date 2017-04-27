package org.skywalking.apm.collector.worker.tools;

import org.junit.Assert;
import org.skywalking.apm.collector.actor.AbstractClusterWorker;
import org.skywalking.apm.collector.worker.Const;
import org.skywalking.apm.collector.worker.mock.MetricDataAnswer;
import org.skywalking.apm.collector.worker.storage.MetricData;

/**
 * @author pengys5
 */
public enum MetricDataAggTools {
    INSTANCE;

    public void testOnWork(AbstractClusterWorker agg, MetricDataAnswer metricDataAnswer) throws Exception {
        String id = "2016" + Const.ID_SPLIT + "A" + Const.ID_SPLIT + "B";
        MetricData metricData = new MetricData(id);
        agg.allocateJob(metricData);
        Assert.assertEquals("A" + Const.ID_SPLIT + "B", metricDataAnswer.getMetricDataList().get(0).asMap().get("aggId"));
    }
}
