package com.a.eye.skywalking.collector.worker.node.analysis;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.WorkerRefs;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.queue.EndOfBatchCommand;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.mock.RecordDataAnswer;
import com.a.eye.skywalking.collector.worker.node.persistence.NodeMappingMinuteAgg;
import com.a.eye.skywalking.collector.worker.segment.SegmentPost;
import com.a.eye.skywalking.collector.worker.segment.mock.SegmentMock;
import com.a.eye.skywalking.collector.worker.storage.RecordData;
import com.a.eye.skywalking.collector.worker.tools.DateTools;
import com.a.eye.skywalking.collector.worker.tools.RecordDataTool;
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
public class NodeMappingMinuteAnalysisTestCase {

    private NodeMappingMinuteAnalysis nodeMappingMinuteAnalysis;
    private SegmentMock segmentMock = new SegmentMock();
    private RecordDataAnswer recordDataAnswer;

    @Before
    public void init() throws Exception {
        ClusterWorkerContext clusterWorkerContext = PowerMockito.mock(ClusterWorkerContext.class);
        WorkerRefs workerRefs = mock(WorkerRefs.class);
        recordDataAnswer = new RecordDataAnswer();
        doAnswer(recordDataAnswer).when(workerRefs).tell(Mockito.any(RecordData.class));

        when(clusterWorkerContext.lookup(NodeMappingMinuteAgg.Role.INSTANCE)).thenReturn(workerRefs);

        LocalWorkerContext localWorkerContext = new LocalWorkerContext();
        nodeMappingMinuteAnalysis = new NodeMappingMinuteAnalysis(NodeMappingMinuteAnalysis.Role.INSTANCE, clusterWorkerContext, localWorkerContext);
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

    @Test
    public void testAnalyse() throws Exception {
        List<SegmentPost.SegmentWithTimeSlice> cacheServiceSegment = segmentMock.mockCacheServiceSegmentSegmentTimeSlice();
        for (SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice : cacheServiceSegment) {
            nodeMappingMinuteAnalysis.analyse(segmentWithTimeSlice);
        }
        List<SegmentPost.SegmentWithTimeSlice> portalService = segmentMock.mockPortalServiceSegmentSegmentTimeSlice();
        for (SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice : portalService) {
            nodeMappingMinuteAnalysis.analyse(segmentWithTimeSlice);
        }
        List<SegmentPost.SegmentWithTimeSlice> persistenceService = segmentMock.mockPersistenceServiceSegmentTimeSlice();
        for (SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice : persistenceService) {
            nodeMappingMinuteAnalysis.analyse(segmentWithTimeSlice);
        }
        List<SegmentPost.SegmentWithTimeSlice> cacheServiceException = segmentMock.mockCacheServiceExceptionSegmentTimeSlice();
        for (SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice : cacheServiceException) {
            nodeMappingMinuteAnalysis.analyse(segmentWithTimeSlice);
        }
        List<SegmentPost.SegmentWithTimeSlice> portalServiceException = segmentMock.mockPortalServiceExceptionSegmentTimeSlice();
        for (SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice : portalServiceException) {
            nodeMappingMinuteAnalysis.analyse(segmentWithTimeSlice);
        }

        nodeMappingMinuteAnalysis.onWork(new EndOfBatchCommand());

        Assert.assertEquals(3, recordDataAnswer.recordObj.getRecordData().size());
        this.verify_1(recordDataAnswer.recordObj.getRecordData(), DateTools.changeToUTCSlice(201703310915L));
        this.verify_2(recordDataAnswer.recordObj.getRecordData(), DateTools.changeToUTCSlice(201703310916L));
    }

    public void verify_1(List<RecordData> recordDataList, long timeSlice) {
        RecordData data_1 = RecordDataTool.INSTANCE.getRecord(recordDataList, timeSlice + "..-..persistence-service..-..[10.128.35.80:20880]");
        Assert.assertEquals("persistence-service", data_1.getRecord().get("code").getAsString());
        Assert.assertEquals("[10.128.35.80:20880]", data_1.getRecord().get("peers").getAsString());
        Assert.assertEquals("persistence-service..-..[10.128.35.80:20880]", data_1.getRecord().get("aggId").getAsString());
        Assert.assertEquals(timeSlice, data_1.getRecord().get("timeSlice").getAsLong());

        RecordData data_2 = RecordDataTool.INSTANCE.getRecord(recordDataList, timeSlice + "..-..cache-service..-..[127.0.0.1:8002]");
        Assert.assertEquals("cache-service", data_2.getRecord().get("code").getAsString());
        Assert.assertEquals("[127.0.0.1:8002]", data_2.getRecord().get("peers").getAsString());
        Assert.assertEquals("cache-service..-..[127.0.0.1:8002]", data_2.getRecord().get("aggId").getAsString());
        Assert.assertEquals(timeSlice, data_2.getRecord().get("timeSlice").getAsLong());
    }

    public void verify_2(List<RecordData> recordDataList, long timeSlice) {
        RecordData data_2 = RecordDataTool.INSTANCE.getRecord(recordDataList, timeSlice + "..-..cache-service..-..[127.0.0.1:8002]");
        Assert.assertEquals("cache-service", data_2.getRecord().get("code").getAsString());
        Assert.assertEquals("[127.0.0.1:8002]", data_2.getRecord().get("peers").getAsString());
        Assert.assertEquals("cache-service..-..[127.0.0.1:8002]", data_2.getRecord().get("aggId").getAsString());
        Assert.assertEquals(timeSlice, data_2.getRecord().get("timeSlice").getAsLong());
    }
}
