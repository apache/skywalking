package org.skywalking.apm.collector.worker.noderef.analysis;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import org.skywalking.apm.collector.worker.datamerge.JsonDataMerge;
import org.skywalking.apm.collector.worker.datamerge.RecordDataMergeJson;
import org.skywalking.apm.collector.worker.mock.RecordDataAnswer;
import org.skywalking.apm.collector.worker.segment.mock.SegmentMock;
import org.skywalking.apm.collector.worker.storage.RecordData;

import java.util.List;

/**
 * @author pengys5
 */
public enum NodeRefAnalyse {
    INSTANCE;

    public void analyse(String resSumJsonFile, String jsonFile, AbstractNodeRefAnalysis analysis,
                        RecordDataAnswer answer, NodeRefResRecordAnswer recordAnswer) throws Exception {
        SegmentMock segmentMock = new SegmentMock();
        segmentMock.executeAnalysis(analysis);

        List<RecordData> recordDataList = answer.getRecordDataList();
        RecordDataMergeJson.INSTANCE.merge(jsonFile, recordDataList);

        Gson gson = new Gson();
        String recordJsonStr = gson.toJson(recordAnswer.getNodeRefResRecordList());
        JsonArray recordJsonArray = gson.fromJson(recordJsonStr, JsonArray.class);
        JsonDataMerge.INSTANCE.merge(resSumJsonFile, recordJsonArray);
    }
}
