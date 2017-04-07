package com.a.eye.skywalking.collector.worker.segment.persistence;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.mock.MockEsBulkClient;
import com.a.eye.skywalking.collector.worker.segment.SegmentExceptionIndex;
import com.a.eye.skywalking.collector.worker.segment.SegmentPost;
import com.a.eye.skywalking.collector.worker.segment.mock.SegmentMock;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({EsClient.class})
@PowerMockIgnore({"javax.management.*"})
public class SegmentExceptionSaveTestCase {

    private Logger logger = LogManager.getFormatterLogger(SegmentExceptionSaveTestCase.class);

    private SegmentMock segmentMock = new SegmentMock();
    private SegmentExceptionSave segmentExceptionSave;
    private SaveToEsSource saveToEsSource;
    private MockEsBulkClient mockEsBulkClient = new MockEsBulkClient();

    @Before
    public void init() {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        mockEsBulkClient.createMock();

        saveToEsSource = new SaveToEsSource();
        when(mockEsBulkClient.indexRequestBuilder.setSource(Mockito.anyString())).thenAnswer(saveToEsSource);

        ClusterWorkerContext clusterWorkerContext = new ClusterWorkerContext(null);
        LocalWorkerContext localWorkerContext = new LocalWorkerContext();

        segmentExceptionSave = new SegmentExceptionSave(SegmentExceptionSave.Role.INSTANCE, clusterWorkerContext, localWorkerContext);
    }

    @Test
    public void testEsIndex() {
        Assert.assertEquals(SegmentExceptionIndex.Index, segmentExceptionSave.esIndex());
    }

    @Test
    public void testEsType() {
        Assert.assertEquals(SegmentExceptionIndex.Type_Record, segmentExceptionSave.esType());
    }

    @Test
    public void testRole() {
        Assert.assertEquals(SegmentExceptionSave.class.getSimpleName(), SegmentExceptionSave.Role.INSTANCE.roleName());
        Assert.assertEquals(RollingSelector.class.getSimpleName(), SegmentExceptionSave.Role.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        Assert.assertEquals(SegmentExceptionSave.class.getSimpleName(), SegmentExceptionSave.Factory.INSTANCE.role().roleName());
        Assert.assertEquals(SegmentExceptionSave.class.getSimpleName(), SegmentExceptionSave.Factory.INSTANCE.workerInstance(null).getClass().getSimpleName());

        int testSize = 10;
        WorkerConfig.Queue.Segment.SegmentExceptionSave.Size = testSize;
        Assert.assertEquals(testSize, SegmentExceptionSave.Factory.INSTANCE.queueSize());
    }

    @Test
    public void testSuccessAnalyse() throws Exception {
        WorkerConfig.Persistence.Data.size = 1;

        List<SegmentPost.SegmentWithTimeSlice> cacheServiceList = segmentMock.mockCacheServiceSegmentSegmentTimeSlice();
        for (SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice : cacheServiceList) {
            segmentExceptionSave.analyse(segmentWithTimeSlice);
        }

        JsonObject isError_1 = saveToEsSource.isErrorMap.get("Segment.1490922929258.927784221.5991.27.1");
        Assert.assertEquals(false, isError_1.get("isError").getAsBoolean());
        Assert.assertEquals(0, isError_1.get("errorKind").getAsJsonArray().size());

        JsonObject isError_2 = saveToEsSource.isErrorMap.get("Segment.1490922929298.927784221.5991.28.1");
        Assert.assertEquals(false, isError_2.get("isError").getAsBoolean());
        Assert.assertEquals(0, isError_2.get("errorKind").getAsJsonArray().size());
    }

    @Test
    public void testErrorAnalyse() throws Exception {
        WorkerConfig.Persistence.Data.size = 1;

        List<SegmentPost.SegmentWithTimeSlice> cacheServiceList = segmentMock.mockCacheServiceExceptionSegmentTimeSlice();
        for (SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice : cacheServiceList) {
            segmentExceptionSave.analyse(segmentWithTimeSlice);
        }

        JsonObject isError_1 = saveToEsSource.isErrorMap.get("Segment.1490923010328.927784221.5991.32.1");
        Assert.assertEquals(true, isError_1.get("isError").getAsBoolean());
        Assert.assertEquals("com.weibo.api.motan.exception.MotanBizException", isError_1.get("errorKind").getAsJsonArray().get(0).getAsString());
    }

    class SaveToEsSource implements Answer<Object> {

        Map<String, JsonObject> isErrorMap = new HashMap<>();

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            Gson gson = new Gson();
            String source = (String) invocation.getArguments()[0];
            JsonObject sourceJsonObj = gson.fromJson(source, JsonObject.class);
            logger.info("es source: %s", sourceJsonObj.toString());
            isErrorMap.put(sourceJsonObj.get("segId").getAsString(), sourceJsonObj);
            return null;
        }
    }
}
