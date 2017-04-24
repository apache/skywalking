package com.a.eye.skywalking.collector.worker.storage;

import com.a.eye.skywalking.collector.worker.Const;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author pengys5
 */
public class MetricPersistenceWindowDataTestCase {

    @Test
    public void testGetElseCreate() {
        String id = "2016" + Const.ID_SPLIT + "A" + Const.ID_SPLIT + "B";

        MetricPersistenceData metricPersistenceData = new MetricPersistenceData();
        metricPersistenceData.holdData();
        MetricData metricData = metricPersistenceData.getElseCreate(id);
        metricData.setMetric("Column_1", 10L);
        Assert.assertEquals(id, metricData.getId());

        MetricData metricData1 = metricPersistenceData.getElseCreate(id);
        Assert.assertEquals(10L, metricData1.asMap().get("Column_1"));
    }

    @Test
    public void testSize() {
        String id = "2016" + Const.ID_SPLIT + "A" + Const.ID_SPLIT + "B";

        MetricPersistenceData metricPersistenceData = new MetricPersistenceData();
        metricPersistenceData.holdData();
        metricPersistenceData.getElseCreate(id);

        Assert.assertEquals(1, metricPersistenceData.getCurrentAndHold().size());
        String id_1 = "2016" + Const.ID_SPLIT + "B" + Const.ID_SPLIT + "C";
        metricPersistenceData.getElseCreate(id_1);
        Assert.assertEquals(2, metricPersistenceData.getCurrentAndHold().size());

        metricPersistenceData.getCurrentAndHold().clear();
        Assert.assertEquals(0, metricPersistenceData.getCurrentAndHold().size());
    }
}
