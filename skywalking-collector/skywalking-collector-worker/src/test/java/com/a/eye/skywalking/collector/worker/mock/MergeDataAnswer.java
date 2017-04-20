package com.a.eye.skywalking.collector.worker.mock;

import com.a.eye.skywalking.collector.worker.storage.MergeData;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author pengys5
 */
public class MergeDataAnswer implements Answer<Object> {

    private List<MergeData> mergeDataList = new ArrayList<>();

    @Override
    public Object answer(InvocationOnMock invocation) throws Throwable {
        MergeData mergeData = (MergeData)invocation.getArguments()[0];
        System.out.printf("id: %s \n", mergeData.getId());

        for (Map.Entry<String, String> entry : mergeData.toMap().entrySet()) {
            System.out.printf("key: %s, value: %s \n", entry.getKey(), entry.getValue());
        }
        mergeDataList.add(mergeData);
        return null;
    }

    public List<MergeData> getMergeDataList() {
        return mergeDataList;
    }
}
