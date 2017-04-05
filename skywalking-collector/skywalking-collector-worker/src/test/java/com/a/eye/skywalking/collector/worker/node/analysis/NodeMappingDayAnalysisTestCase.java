package com.a.eye.skywalking.collector.worker.node.analysis;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.WorkerRefs;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.queue.EndOfBatchCommand;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.mock.RecordDataAnswer;
import com.a.eye.skywalking.collector.worker.node.persistence.NodeMappingDayAgg;
import com.a.eye.skywalking.collector.worker.segment.SegmentPost;
import com.a.eye.skywalking.collector.worker.segment.mock.SegmentMock;
import com.a.eye.skywalking.collector.worker.storage.RecordData;
import com.a.eye.skywalking.collector.worker.tools.DateTools;
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
public class NodeMappingDayAnalysisTestCase {

    private NodeMappingDayAnalysis nodeMappingDayAnalysis;
    private SegmentMock segmentMock = new SegmentMock();
    private RecordDataAnswer recordDataAnswer;

    @Before
    public void init() throws Exception {
        ClusterWorkerContext clusterWorkerContext = PowerMockito.mock(ClusterWorkerContext.class);
        WorkerRefs workerRefs = mock(WorkerRefs.class);
        recordDataAnswer = new RecordDataAnswer();
        doAnswer(recordDataAnswer).when(workerRefs).tell(Mockito.any(RecordData.class));

        when(clusterWorkerContext.lookup(NodeMappingDayAgg.Role.INSTANCE)).thenReturn(workerRefs);

        LocalWorkerContext localWorkerContext = new LocalWorkerContext();
        nodeMappingDayAnalysis = new NodeMappingDayAnalysis(NodeMappingDayAnalysis.Role.INSTANCE, clusterWorkerContext, localWorkerContext);
    }

    @Test
    public void testRole() {
        Assert.assertEquals(NodeMappingDayAnalysis.class.getSimpleName(), NodeMappingDayAnalysis.Role.INSTANCE.roleName());
        Assert.assertEquals(RollingSelector.class.getSimpleName(), NodeMappingDayAnalysis.Role.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        Assert.assertEquals(NodeMappingDayAnalysis.class.getSimpleName(), NodeMappingDayAnalysis.Factory.INSTANCE.role().roleName());
        Assert.assertEquals(NodeMappingDayAnalysis.class.getSimpleName(), NodeMappingDayAnalysis.Factory.INSTANCE.workerInstance(null).getClass().getSimpleName());

        int testSize = 10;
        WorkerConfig.Queue.Node.NodeMappingDayAnalysis.Size = testSize;
        Assert.assertEquals(testSize, NodeMappingDayAnalysis.Factory.INSTANCE.queueSize());
    }

    @Test
    public void testAnalyse() throws Exception {
        List<SegmentPost.SegmentWithTimeSlice> cacheServiceSegment = segmentMock.mockCacheServiceSegmentSegmentTimeSlice();
        for (SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice : cacheServiceSegment) {
            nodeMappingDayAnalysis.analyse(segmentWithTimeSlice);
        }
        List<SegmentPost.SegmentWithTimeSlice> portalService = segmentMock.mockPortalServiceSegmentSegmentTimeSlice();
        for (SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice : portalService) {
            nodeMappingDayAnalysis.analyse(segmentWithTimeSlice);
        }
        List<SegmentPost.SegmentWithTimeSlice> persistenceService = segmentMock.mockPersistenceServiceSegmentTimeSlice();
        for (SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice : persistenceService) {
            nodeMappingDayAnalysis.analyse(segmentWithTimeSlice);
        }
        List<SegmentPost.SegmentWithTimeSlice> cacheServiceException = segmentMock.mockCacheServiceExceptionSegmentTimeSlice();
        for (SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice : cacheServiceException) {
            nodeMappingDayAnalysis.analyse(segmentWithTimeSlice);
        }
        List<SegmentPost.SegmentWithTimeSlice> portalServiceException = segmentMock.mockPortalServiceExceptionSegmentTimeSlice();
        for (SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice : portalServiceException) {
            nodeMappingDayAnalysis.analyse(segmentWithTimeSlice);
        }

        nodeMappingDayAnalysis.onWork(new EndOfBatchCommand());

        NodeMappingAnalysisVerify.INSTANCE.verify(recordDataAnswer.recordObj.getRecordData(), DateTools.changeToUTCSlice(201703310000L));
    }
}
