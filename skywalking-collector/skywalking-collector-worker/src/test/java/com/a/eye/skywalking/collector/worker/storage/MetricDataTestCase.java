package com.a.eye.skywalking.collector.worker.storage;

import com.a.eye.skywalking.collector.worker.Const;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class MetricDataTestCase {

    @Test
    public void testConstruction() {
        String id_1 = "2016" + Const.ID_SPLIT + "B";
        MetricData metricData_1 = new MetricData(id_1);

        Assert.assertEquals(id_1, metricData_1.getId());
        Assert.assertEquals(2016L, metricData_1.toMap().get(AbstractIndex.Time_Slice));
        Assert.assertEquals("B", metricData_1.toMap().get(AbstractIndex.AGG_COLUMN));

        String id_2 = "2017" + Const.ID_SPLIT + "B" + Const.ID_SPLIT + "C";
        MetricData metricData_2 = new MetricData(id_2);

        Assert.assertEquals(id_2, metricData_2.getId());
        Assert.assertEquals(2017L, metricData_2.toMap().get(AbstractIndex.Time_Slice));
        Assert.assertEquals("B" + Const.ID_SPLIT + "C", metricData_2.toMap().get(AbstractIndex.AGG_COLUMN));
    }

    @Test
    public void testSetMetric() {
        String id_1 = "2016" + Const.ID_SPLIT + "B";
        MetricData metricData = new MetricData(id_1);

        metricData.setMetric("Column", 10L);
        Assert.assertEquals(10L, metricData.toMap().get("Column"));

        metricData.setMetric("Column", 10L);
        Assert.assertEquals(20L, metricData.toMap().get("Column"));
    }

    @Test
    public void testMerge() {
        String id_1 = "2016" + Const.ID_SPLIT + "B";
        MetricData metricData_1 = new MetricData(id_1);
        metricData_1.setMetric("Column", 10L);

        MetricData metricData_2 = new MetricData(id_1);
        metricData_2.setMetric("Column", 10L);

        metricData_1.merge(metricData_2);
        Assert.assertEquals(20L, metricData_1.toMap().get("Column"));
    }

    @Test
    public void testMergeMapData() {
        String id_1 = "2016" + Const.ID_SPLIT + "B";
        MetricData metricData_1 = new MetricData(id_1);
        metricData_1.setMetric("Column", 10L);

        Map<String, Object> dbData = new HashMap<>();
        dbData.put("Column", 10L);

        metricData_1.merge(dbData);
        Assert.assertEquals(20L, metricData_1.toMap().get("Column"));
    }
}
