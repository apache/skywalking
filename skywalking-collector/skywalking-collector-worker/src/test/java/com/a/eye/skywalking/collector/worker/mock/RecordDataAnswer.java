package com.a.eye.skywalking.collector.worker.mock;

import com.a.eye.skywalking.collector.worker.storage.RecordData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class RecordDataAnswer implements Answer<Object> {

    private Logger logger = LogManager.getFormatterLogger(RecordDataAnswer.class);

    public RecordObj recordObj = new RecordObj();

    @Override
    public Object answer(InvocationOnMock invocation) throws Throwable {
        RecordData recordData = (RecordData) invocation.getArguments()[0];
        recordObj.setSource(recordData);
        return null;
    }

    public class RecordObj {
        private Map<String, RecordData> recordDataMap;

        public RecordObj() {
            recordDataMap = new HashMap<>();
        }

        public Map<String, RecordData> getRecordDataMap() {
            return recordDataMap;
        }

        public void setSource(RecordData recordData) {
            this.recordDataMap.put(recordData.getId(), recordData);
            logger.info("id: %s, data: %s", recordData.getId(), recordData.getRecord().toString());
        }
    }
}
