package org.skywalking.apm.collector.worker.tools;

import org.skywalking.apm.collector.worker.storage.RecordData;

import java.util.List;

/**
 * @author pengys5
 */
public enum RecordDataTool {

    INSTANCE;

    public RecordData getRecord(List<RecordData> recordDataList, String id) {
        for (RecordData recordData : recordDataList) {
            if (id.equals(recordData.getId())) {
                return recordData;
            }
        }
        return null;
    }
}
