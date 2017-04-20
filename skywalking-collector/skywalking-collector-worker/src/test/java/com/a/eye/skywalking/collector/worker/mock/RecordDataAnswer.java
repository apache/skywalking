package com.a.eye.skywalking.collector.worker.mock;

import com.a.eye.skywalking.collector.worker.storage.RecordData;
import com.google.gson.JsonElement;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author pengys5
 */
public class RecordDataAnswer implements Answer<Object> {

    private List<RecordData> recordDataList = new ArrayList<>();

    @Override
    public Object answer(InvocationOnMock invocation) throws Throwable {
        RecordData recordData = (RecordData)invocation.getArguments()[0];
        System.out.printf("id: %s \n", recordData.getId());
        System.out.println(recordData.getRecord().toString());

        recordDataList.add(recordData);
        return null;
    }

    public List<RecordData> getRecordDataList() {
        return recordDataList;
    }
}
