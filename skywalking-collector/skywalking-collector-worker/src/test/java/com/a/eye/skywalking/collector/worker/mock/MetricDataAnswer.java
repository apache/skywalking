package com.a.eye.skywalking.collector.worker.mock;

import com.a.eye.skywalking.collector.worker.storage.MetricData;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author pengys5
 */
public class MetricDataAnswer implements Answer<Object> {

    private List<MetricData> metricDataList = new ArrayList<>();

    @Override
    public Object answer(InvocationOnMock invocation) throws Throwable {
        MetricData metricData = (MetricData)invocation.getArguments()[0];

        System.out.printf("id: %s \n", metricData.getId());
        metricDataList.add(metricData);
        Gson gson = new Gson();
//        String jsonStr = gson.toJson(metricData.toMap());
//        System.out.printf("data: %s \n", jsonStr);
        return null;
    }

    public List<MetricData> getMetricDataList() {
        return metricDataList;
    }
}
