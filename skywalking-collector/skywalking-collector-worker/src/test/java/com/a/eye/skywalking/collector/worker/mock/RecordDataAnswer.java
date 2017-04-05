package com.a.eye.skywalking.collector.worker.mock;

import com.a.eye.skywalking.collector.worker.storage.RecordData;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;

/**
 * @author pengys5
 */
public class RecordDataAnswer implements Answer<Object> {

    public RecordObj recordObj = new RecordObj();

    @Override
    public Object answer(InvocationOnMock invocation) throws Throwable {
        RecordData recordData = (RecordData) invocation.getArguments()[0];
        recordObj.setSource(recordData);
        return null;
    }

    public class RecordObj {
        private List<RecordData> recordDataList;

        private RecordObj() {
            recordDataList = new ArrayList<>();
        }

        public List<RecordData> getRecordData() {
            return recordDataList;
        }

        private void setSource(RecordData recordData) {
            this.recordDataList.add(recordData);
            System.out.printf("id: %s, data: %s \n", recordData.getId(), recordData.getRecord().toString());
        }
    }
}
