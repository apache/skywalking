package org.skywalking.apm.collector.worker.node.analysis;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.ProviderNotFoundException;
import org.skywalking.apm.collector.actor.WorkerRefs;
import org.skywalking.apm.collector.actor.selector.RollingSelector;
import org.skywalking.apm.collector.worker.config.WorkerConfig;
import org.skywalking.apm.collector.worker.datamerge.RecordDataMergeJson;
import org.skywalking.apm.collector.worker.mock.RecordDataAnswer;
import org.skywalking.apm.collector.worker.node.persistence.NodeMappingDayAgg;
import org.skywalking.apm.collector.worker.noderef.analysis.NodeRefResSumDayAnalysis;
import org.skywalking.apm.collector.worker.segment.mock.SegmentMock;
import org.skywalking.apm.collector.worker.storage.RecordData;

import java.util.List;
import java.util.TimeZone;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest( {ClusterWorkerContext.class})
@PowerMockIgnore( {"javax.management.*"})
public class NodeMappingDayAnalysisTestCase {

    private NodeMappingDayAnalysis analysis;
    private SegmentMock segmentMock = new SegmentMock();
    private RecordDataAnswer answer;
    private ClusterWorkerContext clusterWorkerContext;

    @Before
    public void init() throws Exception {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        clusterWorkerContext = PowerMockito.mock(ClusterWorkerContext.class);
        WorkerRefs workerRefs = mock(WorkerRefs.class);
        answer = new RecordDataAnswer();
        doAnswer(answer).when(workerRefs).tell(Mockito.any(RecordData.class));

        when(clusterWorkerContext.lookup(NodeMappingDayAgg.Role.INSTANCE)).thenReturn(workerRefs);

        LocalWorkerContext localWorkerContext = new LocalWorkerContext();
        analysis = new NodeMappingDayAnalysis(NodeMappingDayAnalysis.Role.INSTANCE, clusterWorkerContext, localWorkerContext);
    }

    @Test
    public void testRole() {
        Assert.assertEquals(NodeMappingDayAnalysis.class.getSimpleName(), NodeMappingDayAnalysis.Role.INSTANCE.roleName());
        Assert.assertEquals(RollingSelector.class.getSimpleName(), NodeMappingDayAnalysis.Role.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        NodeMappingDayAnalysis.Factory factory = new NodeMappingDayAnalysis.Factory();
        Assert.assertEquals(NodeMappingDayAnalysis.class.getSimpleName(), factory.role().roleName());
        Assert.assertEquals(NodeMappingDayAnalysis.class.getSimpleName(), factory.workerInstance(null).getClass().getSimpleName());

        int testSize = 10;
        WorkerConfig.Queue.Node.NodeMappingDayAnalysis.SIZE = testSize;
        Assert.assertEquals(testSize, factory.queueSize());
    }

    @Test(expected = Exception.class)
    public void testPreStart() throws ProviderNotFoundException {
        when(clusterWorkerContext.findProvider(NodeRefResSumDayAnalysis.Role.INSTANCE)).thenThrow(new Exception());
        analysis.preStart();
    }

    String jsonFile = "/json/node/analysis/node_mapping_day_analysis.json";

    @Test
    public void testAnalyse() throws Exception {
        segmentMock.executeAnalysis(analysis);

        List<RecordData> recordDataList = answer.getRecordDataList();
        RecordDataMergeJson.INSTANCE.merge(jsonFile, recordDataList);
    }
}
