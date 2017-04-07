package com.a.eye.skywalking.collector.worker.node.analysis;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.WorkerRefs;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.datamerge.RecordDataMergeJson;
import com.a.eye.skywalking.collector.worker.mock.RecordDataAnswer;
import com.a.eye.skywalking.collector.worker.node.persistence.NodeMappingMinuteAgg;
import com.a.eye.skywalking.collector.worker.segment.mock.SegmentMock;
import com.a.eye.skywalking.collector.worker.storage.RecordData;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.List;
import java.util.TimeZone;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ClusterWorkerContext.class})
@PowerMockIgnore({"javax.management.*"})
public class NodeMappingMinuteAnalysisTestCase {

    private NodeMappingMinuteAnalysis analysis;
    private SegmentMock segmentMock = new SegmentMock();
    private RecordDataAnswer answer;

    @Before
    public void init() throws Exception {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        ClusterWorkerContext clusterWorkerContext = PowerMockito.mock(ClusterWorkerContext.class);
        WorkerRefs workerRefs = mock(WorkerRefs.class);
        answer = new RecordDataAnswer();
        doAnswer(answer).when(workerRefs).tell(Mockito.any(RecordData.class));

        when(clusterWorkerContext.lookup(NodeMappingMinuteAgg.Role.INSTANCE)).thenReturn(workerRefs);

        LocalWorkerContext localWorkerContext = new LocalWorkerContext();
        analysis = new NodeMappingMinuteAnalysis(NodeMappingMinuteAnalysis.Role.INSTANCE, clusterWorkerContext, localWorkerContext);
    }

    @Test
    public void testRole() {
        Assert.assertEquals(NodeMappingMinuteAnalysis.class.getSimpleName(), NodeMappingMinuteAnalysis.Role.INSTANCE.roleName());
        Assert.assertEquals(RollingSelector.class.getSimpleName(), NodeMappingMinuteAnalysis.Role.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        Assert.assertEquals(NodeMappingMinuteAnalysis.class.getSimpleName(), NodeMappingMinuteAnalysis.Factory.INSTANCE.role().roleName());
        Assert.assertEquals(NodeMappingMinuteAnalysis.class.getSimpleName(), NodeMappingMinuteAnalysis.Factory.INSTANCE.workerInstance(null).getClass().getSimpleName());

        int testSize = 10;
        WorkerConfig.Queue.Node.NodeMappingMinuteAnalysis.Size = testSize;
        Assert.assertEquals(testSize, NodeMappingMinuteAnalysis.Factory.INSTANCE.queueSize());
    }

    String jsonFile = "/json/node/analysis/node_mapping_minute_analysis.json";

    @Test
    public void testAnalyse() throws Exception {
        segmentMock.executeAnalysis(analysis);

        List<RecordData> recordDataList = answer.getRecordDataList();
        RecordDataMergeJson.INSTANCE.merge(jsonFile, recordDataList);
    }
}
