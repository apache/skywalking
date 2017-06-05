package org.skywalking.apm.collector.worker.mock;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.skywalking.apm.collector.worker.storage.RecordData;

import java.util.ArrayList;
import java.util.List;

/**
 * @author pengys5
 */
public class RecordDataAnswer implements Answer<Object> {

    private List<RecordData> recordDataList = new ArrayList<>();

    @Override
    public Object answer(InvocationOnMock invocation) throws Throwable {
        RecordData recordData = (RecordData) invocation.getArguments()[0];
        recordDataList.add(recordData);
        return null;
    }

    public List<RecordData> getRecordDataList() {
        return recordDataList;
    }
}
