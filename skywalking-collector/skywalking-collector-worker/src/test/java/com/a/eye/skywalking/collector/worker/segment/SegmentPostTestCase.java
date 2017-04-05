package com.a.eye.skywalking.collector.worker.segment;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.WorkerRef;
import com.a.eye.skywalking.collector.worker.globaltrace.analysis.GlobalTraceAnalysis;
import com.a.eye.skywalking.collector.worker.node.analysis.NodeCompAnalysis;
import com.a.eye.skywalking.collector.worker.node.analysis.NodeMappingDayAnalysis;
import com.a.eye.skywalking.collector.worker.node.analysis.NodeMappingHourAnalysis;
import com.a.eye.skywalking.collector.worker.node.analysis.NodeMappingMinuteAnalysis;
import com.a.eye.skywalking.collector.worker.noderef.analysis.NodeRefDayAnalysis;
import com.a.eye.skywalking.collector.worker.noderef.analysis.NodeRefHourAnalysis;
import com.a.eye.skywalking.collector.worker.noderef.analysis.NodeRefMinuteAnalysis;
import com.a.eye.skywalking.collector.worker.segment.mock.SegmentMock;
import com.a.eye.skywalking.collector.worker.segment.persistence.SegmentCostSave;
import com.a.eye.skywalking.collector.worker.segment.persistence.SegmentExceptionSave;
import com.a.eye.skywalking.collector.worker.segment.persistence.SegmentSave;
import com.a.eye.skywalking.collector.worker.tools.DateTools;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.powermock.api.mockito.PowerMockito.*;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({LocalWorkerContext.class, WorkerRef.class})
@PowerMockIgnore({"javax.management.*"})
public class SegmentPostTestCase {

    private Logger logger = LogManager.getFormatterLogger(SegmentPostTestCase.class);

    private SegmentMock segmentMock;
    private SegmentPost segmentPost;
    private LocalWorkerContext localWorkerContext;

    @Before
    public void init() throws Exception {
        segmentMock = new SegmentMock();

        ClusterWorkerContext clusterWorkerContext = new ClusterWorkerContext(null);
        localWorkerContext = new LocalWorkerContext();

        initSegmentSave();
        initSegmentCostSave();
        initGlobalTraceAnalysis();
        initSegmentExceptionSave();
        initNodeRefAnalysis();
        initNodeCompAnalysis();
        initNodeNodeMappingAnalysis();

        segmentPost = spy(new SegmentPost(SegmentPost.WorkerRole.INSTANCE, clusterWorkerContext, localWorkerContext));
    }

    private SegmentSaveAnswer segmentSaveAnswer_1;
    private SegmentSaveAnswer segmentSaveAnswer_2;

    public void initSegmentSave() throws Exception {
        WorkerRef segmentSave = mock(WorkerRef.class);
        doReturn(SegmentSave.Role.INSTANCE).when(segmentSave, "getRole");
        localWorkerContext.put(segmentSave);

        segmentSaveAnswer_1 = new SegmentSaveAnswer();
        doAnswer(segmentSaveAnswer_1).when(segmentSave).tell(Mockito.argThat(new IsCacheServiceSegment_1()));

        segmentSaveAnswer_2 = new SegmentSaveAnswer();
        doAnswer(segmentSaveAnswer_2).when(segmentSave).tell(Mockito.argThat(new IsCacheServiceSegment_2()));
    }

    private SegmentOtherAnswer segmentCostSaveAnswer;

    public void initSegmentCostSave() throws Exception {
        WorkerRef segmentCostSave = mock(WorkerRef.class);
        doReturn(SegmentCostSave.Role.INSTANCE).when(segmentCostSave, "getRole");
        localWorkerContext.put(segmentCostSave);

        segmentCostSaveAnswer = new SegmentOtherAnswer();
        doAnswer(segmentCostSaveAnswer).when(segmentCostSave).tell(Mockito.argThat(new IsSegmentWithTimeSlice()));
    }

    private SegmentOtherAnswer globalTraceAnalysisAnswer;

    public void initGlobalTraceAnalysis() throws Exception {
        WorkerRef globalTraceAnalysis = mock(WorkerRef.class);
        doReturn(GlobalTraceAnalysis.Role.INSTANCE).when(globalTraceAnalysis, "getRole");
        localWorkerContext.put(globalTraceAnalysis);

        globalTraceAnalysisAnswer = new SegmentOtherAnswer();
        doAnswer(globalTraceAnalysisAnswer).when(globalTraceAnalysis).tell(Mockito.argThat(new IsSegmentWithTimeSlice()));
    }

    private SegmentOtherAnswer segmentExceptionSaveAnswer;

    public void initSegmentExceptionSave() throws Exception {
        WorkerRef segmentExceptionSave = mock(WorkerRef.class);
        doReturn(SegmentExceptionSave.Role.INSTANCE).when(segmentExceptionSave, "getRole");
        localWorkerContext.put(segmentExceptionSave);

        segmentExceptionSaveAnswer = new SegmentOtherAnswer();
        doAnswer(segmentExceptionSaveAnswer).when(segmentExceptionSave).tell(Mockito.argThat(new IsSegmentWithTimeSlice()));
    }

    private SegmentOtherAnswer nodeRefMinuteAnalysisAnswer;
    private SegmentOtherAnswer nodeRefHourAnalysisAnswer;
    private SegmentOtherAnswer nodeRefDayAnalysisAnswer;

    public void initNodeRefAnalysis() throws Exception {
        WorkerRef nodeRefMinuteAnalysis = mock(WorkerRef.class);
        doReturn(NodeRefMinuteAnalysis.Role.INSTANCE).when(nodeRefMinuteAnalysis, "getRole");
        localWorkerContext.put(nodeRefMinuteAnalysis);

        nodeRefMinuteAnalysisAnswer = new SegmentOtherAnswer();
        doAnswer(nodeRefMinuteAnalysisAnswer).when(nodeRefMinuteAnalysis).tell(Mockito.argThat(new IsSegmentWithTimeSlice()));

        WorkerRef nodeRefHourAnalysis = mock(WorkerRef.class);
        doReturn(NodeRefHourAnalysis.Role.INSTANCE).when(nodeRefHourAnalysis, "getRole");
        localWorkerContext.put(nodeRefHourAnalysis);

        nodeRefHourAnalysisAnswer = new SegmentOtherAnswer();
        doAnswer(nodeRefHourAnalysisAnswer).when(nodeRefHourAnalysis).tell(Mockito.argThat(new IsSegmentWithTimeSlice()));

        WorkerRef nodeRefDayAnalysis = mock(WorkerRef.class);
        doReturn(NodeRefDayAnalysis.Role.INSTANCE).when(nodeRefDayAnalysis, "getRole");
        localWorkerContext.put(nodeRefDayAnalysis);

        nodeRefDayAnalysisAnswer = new SegmentOtherAnswer();
        doAnswer(nodeRefDayAnalysisAnswer).when(nodeRefDayAnalysis).tell(Mockito.argThat(new IsSegmentWithTimeSlice()));
    }

    private SegmentOtherAnswer nodeCompAnalysisAnswer;

    public void initNodeCompAnalysis() throws Exception {
        WorkerRef nodeMinuteAnalysis = mock(WorkerRef.class);
        doReturn(NodeCompAnalysis.Role.INSTANCE).when(nodeMinuteAnalysis, "getRole");
        localWorkerContext.put(nodeMinuteAnalysis);

        nodeCompAnalysisAnswer = new SegmentOtherAnswer();
        doAnswer(nodeCompAnalysisAnswer).when(nodeMinuteAnalysis).tell(Mockito.argThat(new IsSegmentWithTimeSlice()));
    }

    private SegmentOtherAnswer nodeMappingMinuteAnalysisAnswer;
    private SegmentOtherAnswer nodeMappingHourAnalysisAnswer;
    private SegmentOtherAnswer nodeMappingDayAnalysisAnswer;

    public void initNodeNodeMappingAnalysis() throws Exception {
        WorkerRef nodeMappingMinuteAnalysis = mock(WorkerRef.class);
        doReturn(NodeMappingMinuteAnalysis.Role.INSTANCE).when(nodeMappingMinuteAnalysis, "getRole");
        localWorkerContext.put(nodeMappingMinuteAnalysis);

        nodeMappingMinuteAnalysisAnswer = new SegmentOtherAnswer();
        doAnswer(nodeMappingMinuteAnalysisAnswer).when(nodeMappingMinuteAnalysis).tell(Mockito.argThat(new IsSegmentWithTimeSlice()));

        WorkerRef nodeMappingHourAnalysis = mock(WorkerRef.class);
        doReturn(NodeMappingHourAnalysis.Role.INSTANCE).when(nodeMappingHourAnalysis, "getRole");
        localWorkerContext.put(nodeMappingHourAnalysis);

        nodeMappingHourAnalysisAnswer = new SegmentOtherAnswer();
        doAnswer(nodeMappingHourAnalysisAnswer).when(nodeMappingHourAnalysis).tell(Mockito.argThat(new IsSegmentWithTimeSlice()));

        WorkerRef nodeMappingDayAnalysis = mock(WorkerRef.class);
        doReturn(NodeMappingDayAnalysis.Role.INSTANCE).when(nodeMappingDayAnalysis, "getRole");
        localWorkerContext.put(nodeMappingDayAnalysis);

        nodeMappingDayAnalysisAnswer = new SegmentOtherAnswer();
        doAnswer(nodeMappingDayAnalysisAnswer).when(nodeMappingDayAnalysis).tell(Mockito.argThat(new IsSegmentWithTimeSlice()));
    }

    @Test
    public void testOnReceive() throws Exception {
        String cacheServiceSegmentAsString = segmentMock.mockCacheServiceSegmentAsString();

        segmentPost.onReceive(cacheServiceSegmentAsString);

        Assert.assertEquals(DateTools.changeToUTCSlice(201703310915L), segmentSaveAnswer_1.minute);
        Assert.assertEquals(DateTools.changeToUTCSlice(201703310900L), segmentSaveAnswer_1.hour);
        Assert.assertEquals(201703310000L, segmentSaveAnswer_1.day);

        Assert.assertEquals(DateTools.changeToUTCSlice(201703310915L), segmentSaveAnswer_2.minute);
        Assert.assertEquals(DateTools.changeToUTCSlice(201703310900L), segmentSaveAnswer_2.hour);
        Assert.assertEquals(201703310000L, segmentSaveAnswer_2.day);

        Assert.assertEquals(DateTools.changeToUTCSlice(201703310915L), segmentCostSaveAnswer.segmentWithTimeSlice.getMinute());
        Assert.assertEquals(DateTools.changeToUTCSlice(201703310900L), segmentCostSaveAnswer.segmentWithTimeSlice.getHour());
        Assert.assertEquals(201703310000L, segmentCostSaveAnswer.segmentWithTimeSlice.getDay());

        Assert.assertEquals(DateTools.changeToUTCSlice(201703310915L), globalTraceAnalysisAnswer.segmentWithTimeSlice.getMinute());
        Assert.assertEquals(DateTools.changeToUTCSlice(201703310900L), globalTraceAnalysisAnswer.segmentWithTimeSlice.getHour());
        Assert.assertEquals(201703310000L, globalTraceAnalysisAnswer.segmentWithTimeSlice.getDay());

        Assert.assertEquals(DateTools.changeToUTCSlice(201703310915L), segmentExceptionSaveAnswer.segmentWithTimeSlice.getMinute());
        Assert.assertEquals(DateTools.changeToUTCSlice(201703310900L), segmentExceptionSaveAnswer.segmentWithTimeSlice.getHour());
        Assert.assertEquals(201703310000L, segmentExceptionSaveAnswer.segmentWithTimeSlice.getDay());

        Assert.assertEquals(DateTools.changeToUTCSlice(201703310915L), nodeRefMinuteAnalysisAnswer.segmentWithTimeSlice.getMinute());
        Assert.assertEquals(DateTools.changeToUTCSlice(201703310900L), nodeRefMinuteAnalysisAnswer.segmentWithTimeSlice.getHour());
        Assert.assertEquals(201703310000L, nodeRefMinuteAnalysisAnswer.segmentWithTimeSlice.getDay());

        Assert.assertEquals(DateTools.changeToUTCSlice(201703310915L), nodeRefHourAnalysisAnswer.segmentWithTimeSlice.getMinute());
        Assert.assertEquals(DateTools.changeToUTCSlice(201703310900L), nodeRefHourAnalysisAnswer.segmentWithTimeSlice.getHour());
        Assert.assertEquals(201703310000L, nodeRefHourAnalysisAnswer.segmentWithTimeSlice.getDay());

        Assert.assertEquals(DateTools.changeToUTCSlice(201703310915L), nodeRefDayAnalysisAnswer.segmentWithTimeSlice.getMinute());
        Assert.assertEquals(DateTools.changeToUTCSlice(201703310900L), nodeRefDayAnalysisAnswer.segmentWithTimeSlice.getHour());
        Assert.assertEquals(201703310000L, nodeRefDayAnalysisAnswer.segmentWithTimeSlice.getDay());
    }

    public class SegmentOtherAnswer implements Answer<Object> {

        SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice;

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            segmentWithTimeSlice = (SegmentPost.SegmentWithTimeSlice) invocation.getArguments()[0];
            return null;
        }
    }

    public class SegmentSaveAnswer implements Answer<Object> {
        long minute = 0;
        long hour = 0;
        long day = 0;

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            JsonObject jsonObject = (JsonObject) invocation.getArguments()[0];
            logger.info("SegmentSave json: " + jsonObject.toString());
            minute = jsonObject.get("minute").getAsLong();
            hour = jsonObject.get("hour").getAsLong();
            day = jsonObject.get("day").getAsLong();
            return null;
        }
    }

    class IsCacheServiceSegment_1 extends ArgumentMatcher<JsonObject> {
        private static final String SegId = "Segment.1490922929258.927784221.5991.27.1";

        public boolean matches(Object para) {
            JsonObject paraJson = (JsonObject) para;
            return SegId.equals(paraJson.get("ts").getAsString());
        }
    }

    class IsCacheServiceSegment_2 extends ArgumentMatcher<JsonObject> {
        private static final String SegId = "Segment.1490922929298.927784221.5991.28.1";

        public boolean matches(Object para) {
            JsonObject paraJson = (JsonObject) para;
            return SegId.equals(paraJson.get("ts").getAsString());
        }
    }

    class IsSegmentWithTimeSlice extends ArgumentMatcher<SegmentPost.SegmentWithTimeSlice> {
        public boolean matches(Object para) {
            return para instanceof SegmentPost.SegmentWithTimeSlice;
        }
    }
}
