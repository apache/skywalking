package com.a.eye.skywalking.collector.worker.storage;

import com.a.eye.skywalking.collector.worker.Const;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author pengys5
 */
public class MetricAnalysisDataTestCase {

    @Test
    public void getOrCreate() {
        String id = "2016" + Const.ID_SPLIT + "A" + Const.ID_SPLIT + "B";
        MetricAnalysisData metricAnalysisData = new MetricAnalysisData();
        MetricData metricData = metricAnalysisData.getOrCreate(id);

        MetricData metricData_1 = metricAnalysisData.getOrCreate(id);
        Assert.assertEquals(metricData, metricData_1);
    }

    @Test
    public void asMap() {
        String id = "2016" + Const.ID_SPLIT + "A" + Const.ID_SPLIT + "B";
        MetricAnalysisData metricAnalysisData = new MetricAnalysisData();
        MetricData metricData = metricAnalysisData.getOrCreate(id);

        MetricData metricData_1 = metricAnalysisData.asMap().get(id);
        Assert.assertEquals(metricData, metricData_1);
    }
}
