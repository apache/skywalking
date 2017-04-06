package com.a.eye.skywalking.collector.worker.storage;

import com.a.eye.skywalking.collector.worker.Const;
import org.junit.Assert;
import org.junit.Test;

import java.util.Iterator;
import java.util.Map;

/**
 * @author pengys5
 */
public class MetricPersistenceDataTestCase {

    @Test
    public void testGetElseCreate() {
        String id = "2016" + Const.ID_SPLIT + "A" + Const.ID_SPLIT + "B";

        MetricPersistenceData metricPersistenceData = new MetricPersistenceData();
        MetricData metricData = metricPersistenceData.getElseCreate(id);
        metricData.setMetric("Column_1", 10L);
        Assert.assertEquals(id, metricData.getId());

        MetricData metricData1 = metricPersistenceData.getElseCreate(id);
        Assert.assertEquals(10L, metricData1.toMap().get("Column_1"));
    }

    @Test
    public void testSize() {
        String id = "2016" + Const.ID_SPLIT + "A" + Const.ID_SPLIT + "B";

        MetricPersistenceData metricPersistenceData = new MetricPersistenceData();
        metricPersistenceData.getElseCreate(id);

        Assert.assertEquals(1, metricPersistenceData.size());
        String id_1 = "2016" + Const.ID_SPLIT + "B" + Const.ID_SPLIT + "C";
        metricPersistenceData.getElseCreate(id_1);
        Assert.assertEquals(2, metricPersistenceData.size());

        metricPersistenceData.clear();
        Assert.assertEquals(0, metricPersistenceData.size());
    }

    @Test
    public void testPushOne() {
        String id_1 = "2016" + Const.ID_SPLIT + "A" + Const.ID_SPLIT + "B";
        String id_2 = "2016" + Const.ID_SPLIT + "B" + Const.ID_SPLIT + "C";

        MetricPersistenceData metricPersistenceData = new MetricPersistenceData();
        metricPersistenceData.getElseCreate(id_1);
        metricPersistenceData.getElseCreate(id_2);

        MetricData metricData_2 = metricPersistenceData.pushOne();
        Assert.assertEquals(id_2, metricData_2.getId());

        MetricData metricData_1 = metricPersistenceData.pushOne();
        Assert.assertEquals(id_1, metricData_1.getId());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSpliterator() {
        MetricPersistenceData metricPersistenceData = new MetricPersistenceData();
        metricPersistenceData.spliterator();
    }

    @Test
    public void testIterator() {
        String id_1 = "2016" + Const.ID_SPLIT + "A" + Const.ID_SPLIT + "B";
        String id_2 = "2016" + Const.ID_SPLIT + "B" + Const.ID_SPLIT + "C";

        MetricPersistenceData metricPersistenceData = new MetricPersistenceData();
        metricPersistenceData.getElseCreate(id_1);
        metricPersistenceData.getElseCreate(id_2);

        Iterator<Map.Entry<String, MetricData>> iterator = metricPersistenceData.iterator();
        Assert.assertEquals(id_2, iterator.next().getKey());
        Assert.assertEquals(id_1, iterator.next().getKey());
    }
}
