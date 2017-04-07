package com.a.eye.skywalking.collector.worker.mock;

import com.a.eye.skywalking.collector.worker.storage.MergeData;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class MergeDataAnswer implements Answer<Object> {

    public Map<String, String> mergeObj = new HashMap<>();

    @Override
    public Object answer(InvocationOnMock invocation) throws Throwable {
        MergeData mergeData = (MergeData) invocation.getArguments()[0];

        for (Map.Entry<String, String> entry : mergeData.toMap().entrySet()) {
            System.out.printf("key: %s, value: %s \n", entry.getKey(), entry.getValue());
            mergeObj.put(entry.getKey(), entry.getValue());
        }
        return null;
    }
}
