package com.a.eye.skywalking.collector.worker.mock;

import com.a.eye.skywalking.collector.worker.storage.RecordData;
import java.util.ArrayList;
import java.util.List;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * @author pengys5
 */
public class RecordDataAnswer implements Answer<Object> {

    private List<RecordData> recordDataList = new ArrayList<>();

    @Override
    public Object answer(InvocationOnMock invocation) throws Throwable {
        RecordData recordData = (RecordData)invocation.getArguments()[0];
        recordDataList.add(recordData);
        return null;
    }

    public List<RecordData> getRecordDataList() {
        return recordDataList;
    }
}
