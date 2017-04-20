package com.a.eye.skywalking.collector.worker.noderef.analysis;

import com.a.eye.skywalking.collector.worker.datamerge.JsonDataMerge;
import com.a.eye.skywalking.collector.worker.datamerge.RecordDataMergeJson;
import com.a.eye.skywalking.collector.worker.mock.RecordDataAnswer;
import com.a.eye.skywalking.collector.worker.segment.mock.SegmentMock;
import com.a.eye.skywalking.collector.worker.storage.RecordData;
import com.google.gson.Gson;
import com.google.gson.JsonArray;

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
        System.out.println(recordJsonStr);

        System.out.println("--------------------------------");
        JsonArray recordJsonArray = gson.fromJson(recordJsonStr, JsonArray.class);
        JsonDataMerge.INSTANCE.merge(resSumJsonFile, recordJsonArray);
    }
}
