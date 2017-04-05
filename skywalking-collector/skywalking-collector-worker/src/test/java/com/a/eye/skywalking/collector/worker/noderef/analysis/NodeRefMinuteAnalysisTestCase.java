package com.a.eye.skywalking.collector.worker.noderef.analysis;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.WorkerRefs;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.queue.EndOfBatchCommand;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.mock.RecordDataAnswer;
import com.a.eye.skywalking.collector.worker.node.analysis.NodeAnalysisVerify;
import com.a.eye.skywalking.collector.worker.noderef.persistence.NodeRefMinuteAgg;
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
    public void testAnalyse() throws Exception {
        List<SegmentPost.SegmentWithTimeSlice> cacheServiceSegment = segmentMock.mockCacheServiceSegmentSegmentTimeSlice();
        for (SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice : cacheServiceSegment) {
            nodeRefMinuteAnalysis.analyse(segmentWithTimeSlice);
        }
        List<SegmentPost.SegmentWithTimeSlice> portalService = segmentMock.mockPortalServiceSegmentSegmentTimeSlice();
        for (SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice : portalService) {
            nodeRefMinuteAnalysis.analyse(segmentWithTimeSlice);
        }
        List<SegmentPost.SegmentWithTimeSlice> persistenceService = segmentMock.mockPersistenceServiceSegmentTimeSlice();
        for (SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice : persistenceService) {
            nodeRefMinuteAnalysis.analyse(segmentWithTimeSlice);
        }
        List<SegmentPost.SegmentWithTimeSlice> cacheServiceException = segmentMock.mockCacheServiceExceptionSegmentTimeSlice();
        for (SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice : cacheServiceException) {
            nodeRefMinuteAnalysis.analyse(segmentWithTimeSlice);
        }
        List<SegmentPost.SegmentWithTimeSlice> portalServiceException = segmentMock.mockPortalServiceExceptionSegmentTimeSlice();
        for (SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice : portalServiceException) {
            nodeRefMinuteAnalysis.analyse(segmentWithTimeSlice);
        }

        nodeRefMinuteAnalysis.onWork(new EndOfBatchCommand());

        Assert.assertEquals(8, recordDataAnswer.recordObj.getRecordData().size());
        this.verify_1(recordDataAnswer.recordObj.getRecordData(), DateTools.changeToUTCSlice(201703310915L));
        this.verify_2(recordDataAnswer.recordObj.getRecordData(), DateTools.changeToUTCSlice(201703310916L));
    }

    public void verify_1(List<RecordData> recordDataList, long timeSlice) {
        RecordData data_5 = RecordDataTool.INSTANCE.getRecord(recordDataList, timeSlice + "..-..portal-service..-..[127.0.0.1:8002]");
        Assert.assertEquals(true, data_5.getRecord().get("frontIsRealCode").getAsBoolean());
        Assert.assertEquals(false, data_5.getRecord().get("behindIsRealCode").getAsBoolean());
        Assert.assertEquals("portal-service", data_5.getRecord().get("front").getAsString());
        Assert.assertEquals("[127.0.0.1:8002]", data_5.getRecord().get("behind").getAsString());
        Assert.assertEquals("portal-service..-..[127.0.0.1:8002]", data_5.getRecord().get("aggId").getAsString());
        Assert.assertEquals(timeSlice, data_5.getRecord().get("timeSlice").getAsLong());

        RecordData data_3 = RecordDataTool.INSTANCE.getRecord(recordDataList, timeSlice + "..-..cache-service..-..[localhost:-1]");
        Assert.assertEquals(true, data_3.getRecord().get("frontIsRealCode").getAsBoolean());
        Assert.assertEquals(false, data_3.getRecord().get("behindIsRealCode").getAsBoolean());
        Assert.assertEquals("cache-service", data_3.getRecord().get("front").getAsString());
        Assert.assertEquals("[localhost:-1]", data_3.getRecord().get("behind").getAsString());
        Assert.assertEquals("cache-service..-..[localhost:-1]", data_3.getRecord().get("aggId").getAsString());
        Assert.assertEquals(timeSlice, data_3.getRecord().get("timeSlice").getAsLong());

        RecordData data_4 = RecordDataTool.INSTANCE.getRecord(recordDataList, timeSlice + "..-..User..-..portal-service");
        Assert.assertEquals(true, data_4.getRecord().get("frontIsRealCode").getAsBoolean());
        Assert.assertEquals(true, data_4.getRecord().get("behindIsRealCode").getAsBoolean());
        Assert.assertEquals("User", data_4.getRecord().get("front").getAsString());
        Assert.assertEquals("portal-service", data_4.getRecord().get("behind").getAsString());
        Assert.assertEquals("User..-..portal-service", data_4.getRecord().get("aggId").getAsString());
        Assert.assertEquals(timeSlice, data_4.getRecord().get("timeSlice").getAsLong());

        RecordData data_2 = RecordDataTool.INSTANCE.getRecord(recordDataList, timeSlice + "..-..cache-service..-..[127.0.0.1:6379]");
        Assert.assertEquals(true, data_2.getRecord().get("frontIsRealCode").getAsBoolean());
        Assert.assertEquals(false, data_2.getRecord().get("behindIsRealCode").getAsBoolean());
        Assert.assertEquals("cache-service", data_2.getRecord().get("front").getAsString());
        Assert.assertEquals("[127.0.0.1:6379]", data_2.getRecord().get("behind").getAsString());
        Assert.assertEquals("cache-service..-..[127.0.0.1:6379]", data_2.getRecord().get("aggId").getAsString());
        Assert.assertEquals(timeSlice, data_2.getRecord().get("timeSlice").getAsLong());

        RecordData data_1 = RecordDataTool.INSTANCE.getRecord(recordDataList, timeSlice + "..-..portal-service..-..[10.128.35.80:20880]");
        Assert.assertEquals(true, data_1.getRecord().get("frontIsRealCode").getAsBoolean());
        Assert.assertEquals(false, data_1.getRecord().get("behindIsRealCode").getAsBoolean());
        Assert.assertEquals("portal-service", data_1.getRecord().get("front").getAsString());
        Assert.assertEquals("[10.128.35.80:20880]", data_1.getRecord().get("behind").getAsString());
        Assert.assertEquals("portal-service..-..[10.128.35.80:20880]", data_1.getRecord().get("aggId").getAsString());
        Assert.assertEquals(timeSlice, data_1.getRecord().get("timeSlice").getAsLong());

        RecordData data_6 = RecordDataTool.INSTANCE.getRecord(recordDataList, timeSlice + "..-..persistence-service..-..[127.0.0.1:3307]");
        Assert.assertEquals(true, data_6.getRecord().get("frontIsRealCode").getAsBoolean());
        Assert.assertEquals(false, data_6.getRecord().get("behindIsRealCode").getAsBoolean());
        Assert.assertEquals("persistence-service", data_6.getRecord().get("front").getAsString());
        Assert.assertEquals("[127.0.0.1:3307]", data_6.getRecord().get("behind").getAsString());
        Assert.assertEquals("persistence-service..-..[127.0.0.1:3307]", data_6.getRecord().get("aggId").getAsString());
        Assert.assertEquals(timeSlice, data_6.getRecord().get("timeSlice").getAsLong());
    }

    public void verify_2(List<RecordData> recordDataList, long timeSlice) {
        RecordData data_5 = RecordDataTool.INSTANCE.getRecord(recordDataList, timeSlice + "..-..portal-service..-..[127.0.0.1:8002]");
        Assert.assertEquals(true, data_5.getRecord().get("frontIsRealCode").getAsBoolean());
        Assert.assertEquals(false, data_5.getRecord().get("behindIsRealCode").getAsBoolean());
        Assert.assertEquals("portal-service", data_5.getRecord().get("front").getAsString());
        Assert.assertEquals("[127.0.0.1:8002]", data_5.getRecord().get("behind").getAsString());
        Assert.assertEquals("portal-service..-..[127.0.0.1:8002]", data_5.getRecord().get("aggId").getAsString());
        Assert.assertEquals(timeSlice, data_5.getRecord().get("timeSlice").getAsLong());

        RecordData data_4 = RecordDataTool.INSTANCE.getRecord(recordDataList, timeSlice + "..-..User..-..portal-service");
        Assert.assertEquals(true, data_4.getRecord().get("frontIsRealCode").getAsBoolean());
        Assert.assertEquals(true, data_4.getRecord().get("behindIsRealCode").getAsBoolean());
        Assert.assertEquals("User", data_4.getRecord().get("front").getAsString());
        Assert.assertEquals("portal-service", data_4.getRecord().get("behind").getAsString());
        Assert.assertEquals("User..-..portal-service", data_4.getRecord().get("aggId").getAsString());
        Assert.assertEquals(timeSlice, data_4.getRecord().get("timeSlice").getAsLong());
    }
}
