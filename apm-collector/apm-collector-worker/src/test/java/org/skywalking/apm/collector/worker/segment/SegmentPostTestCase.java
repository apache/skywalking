package org.skywalking.apm.collector.worker.segment;

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
import org.skywalking.apm.collector.AkkaSystem;
import org.skywalking.apm.collector.actor.*;
import org.skywalking.apm.collector.actor.selector.RollingSelector;
import org.skywalking.apm.collector.worker.config.WorkerConfig;
import org.skywalking.apm.collector.worker.globaltrace.analysis.GlobalTraceAnalysis;
import org.skywalking.apm.collector.worker.instance.heartbeat.HeartBeatAnalysis;
import org.skywalking.apm.collector.worker.instance.heartbeat.HeartBeatDataSave;
import org.skywalking.apm.collector.worker.instance.heartbeat.HeartBeatPersistenceMember;
import org.skywalking.apm.collector.worker.node.analysis.NodeCompAnalysis;
import org.skywalking.apm.collector.worker.node.analysis.NodeMappingDayAnalysis;
import org.skywalking.apm.collector.worker.node.analysis.NodeMappingHourAnalysis;
import org.skywalking.apm.collector.worker.node.analysis.NodeMappingMinuteAnalysis;
import org.skywalking.apm.collector.worker.noderef.analysis.*;
import org.skywalking.apm.collector.worker.segment.analysis.SegmentAnalysis;
import org.skywalking.apm.collector.worker.segment.analysis.SegmentCostAnalysis;
import org.skywalking.apm.collector.worker.segment.analysis.SegmentExceptionAnalysis;
import org.skywalking.apm.collector.worker.segment.mock.SegmentMock;
import org.skywalking.apm.collector.worker.segment.persistence.SegmentCostSave;
import org.skywalking.apm.collector.worker.segment.persistence.SegmentExceptionSave;
import org.skywalking.apm.collector.worker.segment.persistence.SegmentSave;
import org.skywalking.apm.collector.worker.tools.DateTools;

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
@PrepareForTest( {LocalWorkerContext.class, WorkerRef.class})
@PowerMockIgnore( {"javax.management.*"})
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
        initSegmentExceptionAnalysis();
        initSegmentAnalysis();
        initSegmentCostAnalysis();
        initGlobalTraceAnalysis();
    }

    @Test
    public void testRole() {
        Assert.assertEquals(SegmentPost.class.getSimpleName(), SegmentPost.WorkerRole.INSTANCE.roleName());
        Assert.assertEquals(RollingSelector.class.getSimpleName(), SegmentPost.WorkerRole.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        SegmentPost.Factory factory = new SegmentPost.Factory();
        Assert.assertEquals(SegmentPost.class.getSimpleName(), factory.role().roleName());
        Assert.assertEquals(SegmentPost.class.getSimpleName(), factory.workerInstance(null).getClass().getSimpleName());
        Assert.assertEquals("/segments", factory.servletPath());
    }

    @Test
    public void testPreStart() throws ProviderNotFoundException {
        when(clusterWorkerContext.findProvider(GlobalTraceAnalysis.Role.INSTANCE)).thenReturn(new GlobalTraceAnalysis.Factory());
        when(clusterWorkerContext.findProvider(NodeCompAnalysis.Role.INSTANCE)).thenReturn(new NodeCompAnalysis.Factory());

        SegmentAnalysis.Factory segmentAnalysisFactory = new SegmentAnalysis.Factory();
        segmentAnalysisFactory.setClusterContext(clusterWorkerContext);

        when(clusterWorkerContext.findProvider(SegmentAnalysis.Role.INSTANCE)).thenReturn(segmentAnalysisFactory);

        SegmentCostAnalysis.Factory segmentCostAnalysisFactory = new SegmentCostAnalysis.Factory();
        segmentCostAnalysisFactory.setClusterContext(clusterWorkerContext);

        when(clusterWorkerContext.findProvider(SegmentCostAnalysis.Role.INSTANCE)).thenReturn(segmentCostAnalysisFactory);

        SegmentExceptionAnalysis.Factory segmentExceptionAnalysisFactory = new SegmentExceptionAnalysis.Factory();
        segmentExceptionAnalysisFactory.setClusterContext(clusterWorkerContext);
        when(clusterWorkerContext.findProvider(SegmentExceptionAnalysis.Role.INSTANCE)).thenReturn(segmentExceptionAnalysisFactory);

        NodeRefMinuteAnalysis.Factory nodeRefMinuteAnalysisFactory = new NodeRefMinuteAnalysis.Factory();
        nodeRefMinuteAnalysisFactory.setClusterContext(clusterWorkerContext);
        when(clusterWorkerContext.findProvider(NodeRefResSumMinuteAnalysis.Role.INSTANCE)).thenReturn(new NodeRefResSumMinuteAnalysis.Factory());
        when(clusterWorkerContext.findProvider(NodeRefMinuteAnalysis.Role.INSTANCE)).thenReturn(nodeRefMinuteAnalysisFactory);

        NodeRefHourAnalysis.Factory nodeRefHourAnalysisFactory = new NodeRefHourAnalysis.Factory();
        nodeRefHourAnalysisFactory.setClusterContext(clusterWorkerContext);
        when(clusterWorkerContext.findProvider(NodeRefResSumHourAnalysis.Role.INSTANCE)).thenReturn(new NodeRefResSumHourAnalysis.Factory());
        when(clusterWorkerContext.findProvider(NodeRefHourAnalysis.Role.INSTANCE)).thenReturn(nodeRefHourAnalysisFactory);

        NodeRefDayAnalysis.Factory nodeRefDayAnalysisFactory = new NodeRefDayAnalysis.Factory();
        nodeRefDayAnalysisFactory.setClusterContext(clusterWorkerContext);
        when(clusterWorkerContext.findProvider(NodeRefResSumDayAnalysis.Role.INSTANCE)).thenReturn(new NodeRefResSumDayAnalysis.Factory());
        when(clusterWorkerContext.findProvider(NodeRefDayAnalysis.Role.INSTANCE)).thenReturn(nodeRefDayAnalysisFactory);

        when(clusterWorkerContext.findProvider(NodeMappingDayAnalysis.Role.INSTANCE)).thenReturn(new NodeMappingDayAnalysis.Factory());
        when(clusterWorkerContext.findProvider(NodeMappingHourAnalysis.Role.INSTANCE)).thenReturn(new NodeMappingHourAnalysis.Factory());
        when(clusterWorkerContext.findProvider(NodeMappingMinuteAnalysis.Role.INSTANCE)).thenReturn(new NodeMappingMinuteAnalysis.Factory());
        when(clusterWorkerContext.findProvider(HeartBeatAnalysis.Role.INSTANCE)).thenReturn(new HeartBeatAnalysis.Factory());

        ArgumentCaptor<Role> argumentCaptor = ArgumentCaptor.forClass(Role.class);

        segmentPost.preStart();

        verify(clusterWorkerContext, times(18)).findProvider(argumentCaptor.capture());
        Assert.assertEquals(GlobalTraceAnalysis.Role.INSTANCE.roleName(), argumentCaptor.getAllValues().get(0).roleName());

        Assert.assertEquals(SegmentAnalysis.Role.INSTANCE.roleName(), argumentCaptor.getAllValues().get(1).roleName());
        Assert.assertEquals(SegmentSave.Role.INSTANCE.roleName(), argumentCaptor.getAllValues().get(2).roleName());

        Assert.assertEquals(SegmentCostAnalysis.Role.INSTANCE.roleName(), argumentCaptor.getAllValues().get(3).roleName());
        Assert.assertEquals(SegmentCostSave.Role.INSTANCE.roleName(), argumentCaptor.getAllValues().get(4).roleName());

        Assert.assertEquals(SegmentExceptionAnalysis.Role.INSTANCE.roleName(), argumentCaptor.getAllValues().get(5).roleName());
        Assert.assertEquals(SegmentExceptionSave.Role.INSTANCE.roleName(), argumentCaptor.getAllValues().get(6).roleName());

        Assert.assertEquals(NodeRefMinuteAnalysis.Role.INSTANCE.roleName(), argumentCaptor.getAllValues().get(7).roleName());
        Assert.assertEquals(NodeRefResSumMinuteAnalysis.Role.INSTANCE.roleName(), argumentCaptor.getAllValues().get(8).roleName());
        Assert.assertEquals(NodeRefHourAnalysis.Role.INSTANCE.roleName(), argumentCaptor.getAllValues().get(9).roleName());
        Assert.assertEquals(NodeRefResSumHourAnalysis.Role.INSTANCE.roleName(), argumentCaptor.getAllValues().get(10).roleName());
        Assert.assertEquals(NodeRefDayAnalysis.Role.INSTANCE.roleName(), argumentCaptor.getAllValues().get(11).roleName());
        Assert.assertEquals(NodeRefResSumDayAnalysis.Role.INSTANCE.roleName(), argumentCaptor.getAllValues().get(12).roleName());

        Assert.assertEquals(NodeCompAnalysis.Role.INSTANCE.roleName(), argumentCaptor.getAllValues().get(13).roleName());

        Assert.assertEquals(NodeMappingDayAnalysis.Role.INSTANCE.roleName(), argumentCaptor.getAllValues().get(14).roleName());
        Assert.assertEquals(NodeMappingHourAnalysis.Role.INSTANCE.roleName(), argumentCaptor.getAllValues().get(15).roleName());
        Assert.assertEquals(NodeMappingMinuteAnalysis.Role.INSTANCE.roleName(), argumentCaptor.getAllValues().get(16).roleName());
    }

    @Test
    public void testValidateData() throws Exception {
        JsonArray segmentArray = new JsonArray();
        JsonObject segmentJsonObj = new JsonObject();
        segmentJsonObj.addProperty("et", 1491277162066L);
        segmentArray.add(segmentJsonObj);

        segmentPost.onReceive(segmentArray.toString(), new JsonObject());
    }

    private SegmentSaveAnswer segmentSaveAnswer_1;
    private SegmentSaveAnswer segmentSaveAnswer_2;

    public void initSegmentAnalysis() throws Exception {
        when(clusterWorkerContext.findProvider(SegmentSave.Role.INSTANCE)).thenReturn(new SegmentSave.Factory());

        WorkerRef segmentAnalysis = mock(WorkerRef.class);
        doReturn(SegmentAnalysis.Role.INSTANCE).when(segmentAnalysis, "getRole");
        localWorkerContext.put(segmentAnalysis);

        segmentSaveAnswer_1 = new SegmentSaveAnswer();
        doAnswer(segmentSaveAnswer_1).when(segmentAnalysis).tell(Mockito.argThat(new IsCacheServiceSegment_1()));

        segmentSaveAnswer_2 = new SegmentSaveAnswer();
        doAnswer(segmentSaveAnswer_2).when(segmentAnalysis).tell(Mockito.argThat(new IsCacheServiceSegment_2()));
    }

    private SegmentOtherAnswer segmentCostSaveAnswer;

    public void initSegmentCostAnalysis() throws Exception {
        when(clusterWorkerContext.findProvider(SegmentCostSave.Role.INSTANCE)).thenReturn(new SegmentCostSave.Factory());

        WorkerRef segmentCostSave = mock(WorkerRef.class);
        doReturn(SegmentCostAnalysis.Role.INSTANCE).when(segmentCostSave, "getRole");
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

    public void initSegmentExceptionAnalysis() throws Exception {
        when(clusterWorkerContext.findProvider(SegmentExceptionSave.Role.INSTANCE)).thenReturn(new SegmentExceptionSave.Factory());

        WorkerRef segmentExceptionSave = mock(WorkerRef.class);
        doReturn(SegmentExceptionAnalysis.Role.INSTANCE).when(segmentExceptionSave, "getRole");
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

    //    @Test
    public void testOnReceive() throws Exception {
        String cacheServiceSegmentAsString = segmentMock.mockCacheServiceSegmentAsString();

        segmentPost.onReceive(cacheServiceSegmentAsString, new JsonObject());

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
