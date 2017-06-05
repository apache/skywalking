package org.skywalking.apm.collector.worker.noderef.analysis;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.skywalking.apm.collector.queue.EndOfBatchCommand;
import org.skywalking.apm.collector.worker.datamerge.MetricDataMergeJson;
import org.skywalking.apm.collector.worker.mock.MetricDataAnswer;
import org.skywalking.apm.collector.worker.segment.mock.SegmentMock;
import org.skywalking.apm.collector.worker.storage.MetricData;

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
