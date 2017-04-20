package com.a.eye.skywalking.collector.worker.segment.persistence;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.worker.config.CacheSizeConfig;
import com.a.eye.skywalking.collector.worker.config.WorkerConfig;
import com.a.eye.skywalking.collector.worker.mock.MockEsBulkClient;
import com.a.eye.skywalking.collector.worker.mock.SaveToEsSourceAnswer;
import com.a.eye.skywalking.collector.worker.segment.SegmentCostIndex;
import com.a.eye.skywalking.collector.worker.segment.SegmentPost;
import com.a.eye.skywalking.collector.worker.segment.mock.SegmentMock;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.a.eye.skywalking.collector.worker.tools.DateTools;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.elasticsearch.client.Client;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.List;
import java.util.TimeZone;

import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({EsClient.class, Client.class, LogManager.class})
@PowerMockIgnore({"javax.management.*"})
public class SegmentCostSaveTestCase {

    private SegmentCostSave segmentCostSave;
    private SegmentMock segmentMock = new SegmentMock();
    private MockEsBulkClient mockEsBulkClient = new MockEsBulkClient();

    private SaveToEsSourceAnswer saveToEsSourceAnswer;

    @Before
    public void init() throws Exception {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        mockEsBulkClient.createMock();

        saveToEsSourceAnswer = new SaveToEsSourceAnswer();
        when(mockEsBulkClient.indexRequestBuilder.setSource(Mockito.anyString())).thenAnswer(saveToEsSourceAnswer);

        ClusterWorkerContext clusterWorkerContext = new ClusterWorkerContext(null);
        LocalWorkerContext localWorkerContext = new LocalWorkerContext();
        segmentCostSave = new SegmentCostSave(SegmentCostSave.Role.INSTANCE, clusterWorkerContext, localWorkerContext);
    }

    @Test
    public void testEsIndex() {
        Assert.assertEquals(SegmentCostIndex.INDEX, segmentCostSave.esIndex());
    }

    @Test
    public void testEsType() {
        Assert.assertEquals(SegmentCostIndex.TYPE_RECORD, segmentCostSave.esType());
    }

    @Test
    public void testRole() {
        Assert.assertEquals(SegmentCostSave.class.getSimpleName(), SegmentCostSave.Role.INSTANCE.roleName());
        Assert.assertEquals(RollingSelector.class.getSimpleName(), SegmentCostSave.Role.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        Assert.assertEquals(SegmentCostSave.class.getSimpleName(), SegmentCostSave.Factory.INSTANCE.role().roleName());
        Assert.assertEquals(SegmentCostSave.class.getSimpleName(), SegmentCostSave.Factory.INSTANCE.workerInstance(null).getClass().getSimpleName());

        int testSize = 10;
        WorkerConfig.Queue.Segment.SegmentCostSave.SIZE = testSize;
        Assert.assertEquals(testSize, SegmentCostSave.Factory.INSTANCE.queueSize());
    }

    @Test
    public void testPersistenceServiceAnalyse() throws Exception {
        CacheSizeConfig.Cache.Persistence.SIZE = 1;

        List<SegmentPost.SegmentWithTimeSlice> segmentWithTimeSliceList = segmentMock.mockPersistenceServiceSegmentTimeSlice();

        for (SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice : segmentWithTimeSliceList) {
            segmentCostSave.analyse(segmentWithTimeSlice);
        }

        JsonArray sourceArray = saveToEsSourceAnswer.sourceObj.getSource();
        Assert.assertEquals(1, sourceArray.size());

        JsonObject costJsonObj = sourceArray.get(0).getAsJsonObject();
        Assert.assertEquals("Segment.1490922929274.1382198130.5997.47.1", costJsonObj.get("segId").getAsString());
        Assert.assertEquals(1490922929274L, costJsonObj.get("startTime").getAsLong());
        Assert.assertEquals(1490922929288L, costJsonObj.get("END_TIME").getAsLong());
        Assert.assertEquals("/persistence/query", costJsonObj.get("operationName").getAsString());
        Assert.assertEquals(DateTools.changeToUTCSlice(201703310915L), costJsonObj.get("timeSlice").getAsLong());
        Assert.assertEquals(14, costJsonObj.get("cost").getAsInt());
    }

    @Test
    public void testCacheServiceAnalyse() throws Exception {
        CacheSizeConfig.Cache.Persistence.SIZE = 2;

        List<SegmentPost.SegmentWithTimeSlice> segmentWithTimeSliceList = segmentMock.mockCacheServiceSegmentSegmentTimeSlice();

        for (SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice : segmentWithTimeSliceList) {
            segmentCostSave.analyse(segmentWithTimeSlice);
        }

        JsonArray sourceArray = saveToEsSourceAnswer.sourceObj.getSource();
        Assert.assertEquals(2, sourceArray.size());

        JsonObject costJsonObj_0 = null;
        JsonObject costJsonObj_1 = null;
        for (int i = 0; i < sourceArray.size(); i++) {
            JsonObject costJsonObj = sourceArray.get(i).getAsJsonObject();
            if (costJsonObj.get("segId").getAsString().equals("Segment.1490922929258.927784221.5991.27.1")) {
                costJsonObj_0 = costJsonObj;
            } else if (costJsonObj.get("segId").getAsString().equals("Segment.1490922929298.927784221.5991.28.1")) {
                costJsonObj_1 = costJsonObj;
            }
        }

        Assert.assertEquals(1490922929258L, costJsonObj_0.get("startTime").getAsLong());
        Assert.assertEquals(1490922929261L, costJsonObj_0.get("END_TIME").getAsLong());
        Assert.assertEquals("com.a.eye.skywalking.test.cache.CacheService.findCache(java.lang.String)", costJsonObj_0.get("operationName").getAsString());
        Assert.assertEquals(DateTools.changeToUTCSlice(201703310915L), costJsonObj_0.get("timeSlice").getAsLong());
        Assert.assertEquals(3, costJsonObj_0.get("cost").getAsInt());

        Assert.assertEquals(1490922929298L, costJsonObj_1.get("startTime").getAsLong());
        Assert.assertEquals(1490922929303L, costJsonObj_1.get("END_TIME").getAsLong());
        Assert.assertEquals("com.a.eye.skywalking.test.cache.CacheService.updateCache(java.lang.String,java.lang.String)", costJsonObj_1.get("operationName").getAsString());
        Assert.assertEquals(DateTools.changeToUTCSlice(201703310915L), costJsonObj_1.get("timeSlice").getAsLong());
        Assert.assertEquals(5, costJsonObj_1.get("cost").getAsInt());
    }

    @Test
    public void testPortalServiceAnalyse() throws Exception {
        CacheSizeConfig.Cache.Persistence.SIZE = 1;

        List<SegmentPost.SegmentWithTimeSlice> segmentWithTimeSliceList = segmentMock.mockPortalServiceSegmentSegmentTimeSlice();

        for (SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice : segmentWithTimeSliceList) {
            segmentCostSave.analyse(segmentWithTimeSlice);
        }

        JsonArray sourceArray = saveToEsSourceAnswer.sourceObj.getSource();
        Assert.assertEquals(1, sourceArray.size());

        JsonObject costJsonObj = sourceArray.get(0).getAsJsonObject();
        Assert.assertEquals("Segment.1490922929254.1797892356.6003.69.1", costJsonObj.get("segId").getAsString());
        Assert.assertEquals(1490922929254L, costJsonObj.get("startTime").getAsLong());
        Assert.assertEquals(1490922929306L, costJsonObj.get("END_TIME").getAsLong());
        Assert.assertEquals("/portal/", costJsonObj.get("operationName").getAsString());
        Assert.assertEquals(DateTools.changeToUTCSlice(201703310915L), costJsonObj.get("timeSlice").getAsLong());
        Assert.assertEquals(52, costJsonObj.get("cost").getAsInt());
    }

    @Test
    public void testCacheServiceExceptionAnalyse() throws Exception {
        CacheSizeConfig.Cache.Persistence.SIZE = 1;

        List<SegmentPost.SegmentWithTimeSlice> segmentWithTimeSliceList = segmentMock.mockCacheServiceExceptionSegmentTimeSlice();

        for (SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice : segmentWithTimeSliceList) {
            segmentCostSave.analyse(segmentWithTimeSlice);
        }

        JsonArray sourceArray = saveToEsSourceAnswer.sourceObj.getSource();
        Assert.assertEquals(1, sourceArray.size());

        JsonObject costJsonObj = sourceArray.get(0).getAsJsonObject();
        Assert.assertEquals("Segment.1490923010328.927784221.5991.32.1", costJsonObj.get("segId").getAsString());
        Assert.assertEquals(1490923010328L, costJsonObj.get("startTime").getAsLong());
        Assert.assertEquals(1490923010329L, costJsonObj.get("END_TIME").getAsLong());
        Assert.assertEquals("com.a.eye.skywalking.test.cache.CacheService.findCacheWithException(java.lang.String)", costJsonObj.get("operationName").getAsString());
        Assert.assertEquals(DateTools.changeToUTCSlice(201703310916L), costJsonObj.get("timeSlice").getAsLong());
        Assert.assertEquals(1, costJsonObj.get("cost").getAsInt());
    }

    @Test
    public void testPortalServiceExceptionAnalyse() throws Exception {
        CacheSizeConfig.Cache.Persistence.SIZE = 1;

        List<SegmentPost.SegmentWithTimeSlice> segmentWithTimeSliceList = segmentMock.mockPortalServiceExceptionSegmentTimeSlice();

        for (SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice : segmentWithTimeSliceList) {
            segmentCostSave.analyse(segmentWithTimeSlice);
        }

        JsonArray sourceArray = saveToEsSourceAnswer.sourceObj.getSource();
        Assert.assertEquals(1, sourceArray.size());

        JsonObject costJsonObj = sourceArray.get(0).getAsJsonObject();
        Assert.assertEquals("Segment.1490923010324.1797892356.6003.67.1", costJsonObj.get("segId").getAsString());
        Assert.assertEquals(1490923010324L, costJsonObj.get("startTime").getAsLong());
        Assert.assertEquals(1490923010336L, costJsonObj.get("END_TIME").getAsLong());
        Assert.assertEquals("/portal/cache/exception/test", costJsonObj.get("operationName").getAsString());
        Assert.assertEquals(DateTools.changeToUTCSlice(201703310916L), costJsonObj.get("timeSlice").getAsLong());
        Assert.assertEquals(12, costJsonObj.get("cost").getAsInt());
    }
}
