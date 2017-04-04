package com.a.eye.skywalking.collector.worker.node.analysis;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.WorkerRefs;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.mock.RecordDataAnswer;
import com.a.eye.skywalking.collector.worker.node.persistence.NodeDayAgg;
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

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ClusterWorkerContext.class})
@PowerMockIgnore({"javax.management.*"})
public class NodeDayAnalysisTestCase {

    private NodeDayAnalysis nodeDayAnalysis;
    private SegmentMock segmentMock = new SegmentMock();
    private RecordDataAnswer recordDataAnswer;

    @Before
    public void init() throws Exception {
        ClusterWorkerContext clusterWorkerContext = PowerMockito.mock(ClusterWorkerContext.class);
        WorkerRefs workerRefs = mock(WorkerRefs.class);
        recordDataAnswer = new RecordDataAnswer();
        doAnswer(recordDataAnswer).when(workerRefs).tell(Mockito.any(RecordData.class));

        when(clusterWorkerContext.lookup(NodeDayAgg.Role.INSTANCE)).thenReturn(workerRefs);

        LocalWorkerContext localWorkerContext = new LocalWorkerContext();
        nodeDayAnalysis = new NodeDayAnalysis(NodeDayAnalysis.Role.INSTANCE, clusterWorkerContext, localWorkerContext);
    }

    @Test
    public void testRole() {
        Assert.assertEquals(NodeDayAnalysis.class.getSimpleName(), NodeDayAnalysis.Role.INSTANCE.roleName());
        Assert.assertEquals(RollingSelector.class.getSimpleName(), NodeDayAnalysis.Role.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        Assert.assertEquals(NodeDayAnalysis.class.getSimpleName(), NodeDayAnalysis.Factory.INSTANCE.role().roleName());
        Assert.assertEquals(NodeDayAnalysis.class.getSimpleName(), NodeDayAnalysis.Factory.INSTANCE.workerInstance(null).getClass().getSimpleName());

        int testSize = 10;
        WorkerConfig.Queue.Node.NodeDayAnalysis.Size = testSize;
        Assert.assertEquals(testSize, NodeDayAnalysis.Factory.INSTANCE.queueSize());
    }

    @Test
    public void testCacheServiceAnalyse() throws Exception {
        WorkerConfig.Analysis.Data.size = 1;

        List<SegmentPost.SegmentWithTimeSlice> segmentWithTimeSliceList = segmentMock.mockCacheServiceSegmentSegmentTimeSlice();
        for (SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice : segmentWithTimeSliceList) {
            nodeDayAnalysis.analyse(segmentWithTimeSlice);
        }

        NodeAnalysisVerfiy.INSTANCE.verfiyCacheService(recordDataAnswer.recordObj.getRecordDataMap(), 201703310000L);
    }

    @Test
    public void testPortalServiceAnalyse() throws Exception {
        WorkerConfig.Analysis.Data.size = 1;

        List<SegmentPost.SegmentWithTimeSlice> segmentWithTimeSliceList = segmentMock.mockPortalServiceSegmentSegmentTimeSlice();
        for (SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice : segmentWithTimeSliceList) {
            nodeDayAnalysis.analyse(segmentWithTimeSlice);
        }

        NodeAnalysisVerfiy.INSTANCE.verfiyPortalService(recordDataAnswer.recordObj.getRecordDataMap(), 201703310000L);
    }

    @Test
    public void testPersistenceServiceAnalyse() throws Exception {
        WorkerConfig.Analysis.Data.size = 1;

        List<SegmentPost.SegmentWithTimeSlice> segmentWithTimeSliceList = segmentMock.mockPersistenceServiceSegmentTimeSlice();
        for (SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice : segmentWithTimeSliceList) {
            nodeDayAnalysis.analyse(segmentWithTimeSlice);
        }

        NodeAnalysisVerfiy.INSTANCE.verfiyPersistenceService(recordDataAnswer.recordObj.getRecordDataMap(), 201703310000L);
    }
}
