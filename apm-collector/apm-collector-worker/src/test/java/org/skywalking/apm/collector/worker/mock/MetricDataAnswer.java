package org.skywalking.apm.collector.worker.mock;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.skywalking.apm.collector.worker.storage.MetricData;

import java.util.ArrayList;
import java.util.List;

/**
 * @author pengys5
 */
public class MetricDataAnswer implements Answer<Object> {

    private List<MetricData> metricDataList = new ArrayList<>();

    @Override
    public Object answer(InvocationOnMock invocation) throws Throwable {
        MetricData metricData = (MetricData) invocation.getArguments()[0];
        metricDataList.add(metricData);
        return null;
    }

    public List<MetricData> getMetricDataList() {
        return metricDataList;
    }
}
