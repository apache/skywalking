package com.a.eye.skywalking.collector.worker.noderef.analysis;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.WorkerRefs;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.mock.RecordDataAnswer;
import com.a.eye.skywalking.collector.worker.noderef.persistence.NodeRefMinuteAgg;
import com.a.eye.skywalking.collector.worker.segment.SegmentPost;
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
import java.util.Map;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ClusterWorkerContext.class})
@PowerMockIgnore({"javax.management.*"})
public class NodeRefMinuteAnalysisTestCase {

    private NodeRefMinuteAnalysis nodeRefMinuteAnalysis;
    private SegmentMock segmentMock = new SegmentMock();
    private RecordDataAnswer recordDataAnswer;

    @Before
    public void init() throws Exception {
        ClusterWorkerContext clusterWorkerContext = PowerMockito.mock(ClusterWorkerContext.class);
        WorkerRefs workerRefs = mock(WorkerRefs.class);
        recordDataAnswer = new RecordDataAnswer();
        doAnswer(recordDataAnswer).when(workerRefs).tell(Mockito.any(RecordData.class));

        when(clusterWorkerContext.lookup(NodeRefMinuteAgg.Role.INSTANCE)).thenReturn(workerRefs);

        LocalWorkerContext localWorkerContext = PowerMockito.mock(LocalWorkerContext.class);
        WorkerRefs nodeRefResSumWorkerRefs = mock(WorkerRefs.class);
        when(localWorkerContext.lookup(NodeRefResSumMinuteAnalysis.Role.INSTANCE)).thenReturn(nodeRefResSumWorkerRefs);
        nodeRefMinuteAnalysis = new NodeRefMinuteAnalysis(NodeRefMinuteAnalysis.Role.INSTANCE, clusterWorkerContext, localWorkerContext);
    }

    @Test
    public void testRole() {
        Assert.assertEquals(NodeRefMinuteAnalysis.class.getSimpleName(), NodeRefMinuteAnalysis.Role.INSTANCE.roleName());
        Assert.assertEquals(RollingSelector.class.getSimpleName(), NodeRefMinuteAnalysis.Role.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        Assert.assertEquals(NodeRefMinuteAnalysis.class.getSimpleName(), NodeRefMinuteAnalysis.Factory.INSTANCE.role().roleName());
        Assert.assertEquals(NodeRefMinuteAnalysis.class.getSimpleName(), NodeRefMinuteAnalysis.Factory.INSTANCE.workerInstance(null).getClass().getSimpleName());

        int testSize = 10;
        WorkerConfig.Queue.Node.NodeRefMinuteAnalysis.Size = testSize;
        Assert.assertEquals(testSize, NodeRefMinuteAnalysis.Factory.INSTANCE.queueSize());
    }

    @Test
    public void testCacheServiceAnalyse() throws Exception {
        WorkerConfig.Analysis.Data.size = 1;

        List<SegmentPost.SegmentWithTimeSlice> segmentWithTimeSliceList = segmentMock.mockCacheServiceSegmentSegmentTimeSlice();
        for (SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice : segmentWithTimeSliceList) {
            nodeRefMinuteAnalysis.analyse(segmentWithTimeSlice);
        }

        Map<String, RecordData> recordDataMap = recordDataAnswer.recordObj.getRecordDataMap();
        for (Map.Entry<String, RecordData> entry : recordDataMap.entrySet()) {
            String id = entry.getKey();
            RecordData recordData = entry.getValue();
            System.out.println(id);
            System.out.println(recordData.getRecord().toString());
        }
    }

    @Test
    public void testPortalServiceAnalyse() throws Exception {
        WorkerConfig.Analysis.Data.size = 1;

        List<SegmentPost.SegmentWithTimeSlice> segmentWithTimeSliceList = segmentMock.mockPortalServiceSegmentSegmentTimeSlice();
        for (SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice : segmentWithTimeSliceList) {
            nodeRefMinuteAnalysis.analyse(segmentWithTimeSlice);
        }

        Map<String, RecordData> recordDataMap = recordDataAnswer.recordObj.getRecordDataMap();
        for (Map.Entry<String, RecordData> entry : recordDataMap.entrySet()) {
            String id = entry.getKey();
            RecordData recordData = entry.getValue();
            System.out.println(id);
            System.out.println(recordData.getRecord().toString());
        }
    }

    @Test
    public void testPersistenceServiceAnalyse() throws Exception {
        WorkerConfig.Analysis.Data.size = 1;

        List<SegmentPost.SegmentWithTimeSlice> segmentWithTimeSliceList = segmentMock.mockPersistenceServiceSegmentTimeSlice();
        for (SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice : segmentWithTimeSliceList) {
            nodeRefMinuteAnalysis.analyse(segmentWithTimeSlice);
        }

        Map<String, RecordData> recordDataMap = recordDataAnswer.recordObj.getRecordDataMap();
        for (Map.Entry<String, RecordData> entry : recordDataMap.entrySet()) {
            String id = entry.getKey();
            RecordData recordData = entry.getValue();
            System.out.println(id);
            System.out.println(recordData.getRecord().toString());
        }
    }
}
