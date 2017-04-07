package com.a.eye.skywalking.collector.worker.mock;

import com.a.eye.skywalking.collector.worker.storage.MetricData;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class MetricDataAnswer implements Answer<Object> {

    public Map<String, Object> metricObj = new HashMap<>();

    @Override
    public Object answer(InvocationOnMock invocation) throws Throwable {
        MetricData metricData = (MetricData) invocation.getArguments()[0];
        for (Map.Entry<String, Object> entry : metricData.toMap().entrySet()) {
            metricObj.put(entry.getKey(), entry.getValue());
        }
        return null;
    }
}
