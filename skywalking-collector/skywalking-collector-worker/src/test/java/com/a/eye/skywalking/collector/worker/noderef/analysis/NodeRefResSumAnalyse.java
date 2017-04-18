package com.a.eye.skywalking.collector.worker.noderef.analysis;

import com.a.eye.skywalking.collector.queue.EndOfBatchCommand;
import com.a.eye.skywalking.collector.worker.datamerge.MetricDataMergeJson;
import com.a.eye.skywalking.collector.worker.mock.MetricDataAnswer;
import com.a.eye.skywalking.collector.worker.segment.mock.SegmentMock;
import com.a.eye.skywalking.collector.worker.storage.MetricData;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.List;

/**
 * @author pengys5
 */
public enum NodeRefResSumAnalyse {
    INSTANCE;

    public void analyse(String requestJsonFile, String jsonFile, AbstractNodeRefResSumAnalysis analysis,
        MetricDataAnswer answer) throws Exception {
        SegmentMock segmentMock = new SegmentMock();
        String requestJsonStr = segmentMock.loadJsonFile(requestJsonFile);
        Gson gson = new Gson();
        List<AbstractNodeRefResSumAnalysis.NodeRefResRecord> resRecordList = gson.fromJson(requestJsonStr, new TypeToken<List<AbstractNodeRefResSumAnalysis.NodeRefResRecord>>() {
        }.getType());

        for (AbstractNodeRefResSumAnalysis.NodeRefResRecord resRecord : resRecordList) {
            analysis.analyse(resRecord);
        }

        analysis.onWork(new EndOfBatchCommand());
        List<MetricData> metricDataList = answer.getMetricDataList();
        MetricDataMergeJson.INSTANCE.merge(jsonFile, metricDataList);
    }
}
