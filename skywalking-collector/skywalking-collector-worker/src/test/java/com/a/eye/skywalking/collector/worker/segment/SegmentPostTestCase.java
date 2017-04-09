package com.a.eye.skywalking.collector.worker.segment;

import com.a.eye.skywalking.collector.actor.*;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.worker.config.WorkerConfig;
import com.a.eye.skywalking.collector.worker.globaltrace.analysis.GlobalTraceAnalysis;
import com.a.eye.skywalking.collector.worker.node.analysis.NodeCompAnalysis;
import com.a.eye.skywalking.collector.worker.node.analysis.NodeMappingDayAnalysis;
import com.a.eye.skywalking.collector.worker.node.analysis.NodeMappingHourAnalysis;
import com.a.eye.skywalking.collector.worker.node.analysis.NodeMappingMinuteAnalysis;
import com.a.eye.skywalking.collector.worker.noderef.analysis.*;
import com.a.eye.skywalking.collector.worker.segment.mock.SegmentMock;
import com.a.eye.skywalking.collector.worker.segment.persistence.SegmentCostSave;
import com.a.eye.skywalking.collector.worker.segment.persistence.SegmentExceptionSave;
import com.a.eye.skywalking.collector.worker.segment.persistence.SegmentSave;
import com.a.eye.skywalking.collector.worker.tools.DateTools;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.TimeZone;

import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.doAnswer;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.mock;

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
    private ClusterWorkerContext clusterWorkerContext;

    @Before
    public void init() throws Exception {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        segmentMock = new SegmentMock();
        clusterWorkerContext = PowerMockito.mock(ClusterWorkerContext.class);
        localWorkerContext = new LocalWorkerContext();

        segmentPost = new SegmentPost(SegmentPost.WorkerRole.INSTANCE, clusterWorkerContext, localWorkerContext);

        initNodeNodeMappingAnalysis();
        initNodeCompAnalysis();
        initNodeRefAnalysis();
        initSegmentExceptionSave();
        initSegmentSave();
        initSegmentCostSave();
        initGlobalTraceAnalysis();
    }

    @Test
    public void testRole() {
        Assert.assertEquals(SegmentPost.class.getSimpleName(), SegmentPost.WorkerRole.INSTANCE.roleName());
        Assert.assertEquals(RollingSelector.class.getSimpleName(), SegmentPost.WorkerRole.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        Assert.assertEquals(SegmentPost.class.getSimpleName(), SegmentPost.Factory.INSTANCE.role().roleName());
        Assert.assertEquals(SegmentPost.class.getSimpleName(), SegmentPost.Factory.INSTANCE.workerInstance(null).getClass().getSimpleName());
        Assert.assertEquals("/segments", SegmentPost.Factory.INSTANCE.servletPath());

        int testSize = 10;
        WorkerConfig.Queue.Segment.SegmentPost.Size = testSize;
        Assert.assertEquals(testSize, SegmentPost.Factory.INSTANCE.queueSize());
    }

    @Test
    public void testPreStart() throws ProviderNotFoundException {
        when(clusterWorkerContext.findProvider(GlobalTraceAnalysis.Role.INSTANCE)).thenReturn(GlobalTraceAnalysis.Factory.INSTANCE);
        when(clusterWorkerContext.findProvider(NodeCompAnalysis.Role.INSTANCE)).thenReturn(NodeCompAnalysis.Factory.INSTANCE);
        when(clusterWorkerContext.findProvider(SegmentSave.Role.INSTANCE)).thenReturn(SegmentSave.Factory.INSTANCE);
        when(clusterWorkerContext.findProvider(SegmentCostSave.Role.INSTANCE)).thenReturn(SegmentCostSave.Factory.INSTANCE);
        when(clusterWorkerContext.findProvider(SegmentExceptionSave.Role.INSTANCE)).thenReturn(SegmentExceptionSave.Factory.INSTANCE);

        NodeRefMinuteAnalysis.Factory.INSTANCE.setClusterContext(clusterWorkerContext);
        when(clusterWorkerContext.findProvider(NodeRefResSumMinuteAnalysis.Role.INSTANCE)).thenReturn(NodeRefResSumMinuteAnalysis.Factory.INSTANCE);
        when(clusterWorkerContext.findProvider(NodeRefMinuteAnalysis.Role.INSTANCE)).thenReturn(NodeRefMinuteAnalysis.Factory.INSTANCE);

        NodeRefHourAnalysis.Factory.INSTANCE.setClusterContext(clusterWorkerContext);
        when(clusterWorkerContext.findProvider(NodeRefResSumHourAnalysis.Role.INSTANCE)).thenReturn(NodeRefResSumHourAnalysis.Factory.INSTANCE);
        when(clusterWorkerContext.findProvider(NodeRefHourAnalysis.Role.INSTANCE)).thenReturn(NodeRefHourAnalysis.Factory.INSTANCE);

        NodeRefDayAnalysis.Factory.INSTANCE.setClusterContext(clusterWorkerContext);
        when(clusterWorkerContext.findProvider(NodeRefResSumDayAnalysis.Role.INSTANCE)).thenReturn(NodeRefResSumDayAnalysis.Factory.INSTANCE);
        when(clusterWorkerContext.findProvider(NodeRefDayAnalysis.Role.INSTANCE)).thenReturn(NodeRefDayAnalysis.Factory.INSTANCE);

        when(clusterWorkerContext.findProvider(NodeMappingDayAnalysis.Role.INSTANCE)).thenReturn(NodeMappingDayAnalysis.Factory.INSTANCE);
        when(clusterWorkerContext.findProvider(NodeMappingHourAnalysis.Role.INSTANCE)).thenReturn(NodeMappingHourAnalysis.Factory.INSTANCE);
        when(clusterWorkerContext.findProvider(NodeMappingMinuteAnalysis.Role.INSTANCE)).thenReturn(NodeMappingMinuteAnalysis.Factory.INSTANCE);

        ArgumentCaptor<Role> argumentCaptor = ArgumentCaptor.forClass(Role.class);

        segmentPost.preStart();

        verify(clusterWorkerContext, times(14)).findProvider(argumentCaptor.capture());
        Assert.assertEquals(GlobalTraceAnalysis.Role.INSTANCE.roleName(), argumentCaptor.getAllValues().get(0).roleName());
        Assert.assertEquals(NodeCompAnalysis.Role.INSTANCE.roleName(), argumentCaptor.getAllValues().get(1).roleName());
        Assert.assertEquals(SegmentSave.Role.INSTANCE.roleName(), argumentCaptor.getAllValues().get(2).roleName());
        Assert.assertEquals(SegmentCostSave.Role.INSTANCE.roleName(), argumentCaptor.getAllValues().get(3).roleName());
        Assert.assertEquals(SegmentExceptionSave.Role.INSTANCE.roleName(), argumentCaptor.getAllValues().get(4).roleName());
        Assert.assertEquals(NodeRefMinuteAnalysis.Role.INSTANCE.roleName(), argumentCaptor.getAllValues().get(5).roleName());
        Assert.assertEquals(NodeRefResSumMinuteAnalysis.Role.INSTANCE.roleName(), argumentCaptor.getAllValues().get(6).roleName());
        Assert.assertEquals(NodeRefHourAnalysis.Role.INSTANCE.roleName(), argumentCaptor.getAllValues().get(7).roleName());
        Assert.assertEquals(NodeRefResSumHourAnalysis.Role.INSTANCE.roleName(), argumentCaptor.getAllValues().get(8).roleName());
        Assert.assertEquals(NodeRefDayAnalysis.Role.INSTANCE.roleName(), argumentCaptor.getAllValues().get(9).roleName());
        Assert.assertEquals(NodeRefResSumDayAnalysis.Role.INSTANCE.roleName(), argumentCaptor.getAllValues().get(10).roleName());
        Assert.assertEquals(NodeMappingDayAnalysis.Role.INSTANCE.roleName(), argumentCaptor.getAllValues().get(11).roleName());
        Assert.assertEquals(NodeMappingHourAnalysis.Role.INSTANCE.roleName(), argumentCaptor.getAllValues().get(12).roleName());
        Assert.assertEquals(NodeMappingMinuteAnalysis.Role.INSTANCE.roleName(), argumentCaptor.getAllValues().get(13).roleName());
    }

    @Test
    public void testValidateData() throws Exception {
        JsonArray segmentArray = new JsonArray();
        JsonObject segmentJsonObj = new JsonObject();
        segmentJsonObj.addProperty("et", 1491277162066L);
        segmentArray.add(segmentJsonObj);

        segmentPost.onReceive(segmentArray.toString());
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
