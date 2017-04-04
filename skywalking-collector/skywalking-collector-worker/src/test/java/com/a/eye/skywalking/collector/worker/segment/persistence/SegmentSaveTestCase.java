package com.a.eye.skywalking.collector.worker.segment.persistence;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.mock.MockEsBulkClient;
import com.a.eye.skywalking.collector.worker.segment.SegmentCostIndex;
import com.a.eye.skywalking.collector.worker.segment.SegmentIndex;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
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

import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({EsClient.class})
@PowerMockIgnore({"javax.management.*"})
public class SegmentSaveTestCase {

    private SegmentSave segmentSave;
    private SaveToEsSource saveToEsSource;
    private MockEsBulkClient mockEsBulkClient = new MockEsBulkClient();

    @Before
    public void init() {
        mockEsBulkClient.createMock();

        saveToEsSource = new SaveToEsSource();
        when(mockEsBulkClient.indexRequestBuilder.setSource(Mockito.anyString())).thenAnswer(saveToEsSource);

        ClusterWorkerContext clusterWorkerContext = new ClusterWorkerContext(null);
        LocalWorkerContext localWorkerContext = new LocalWorkerContext();

        segmentSave = new SegmentSave(SegmentSave.Role.INSTANCE, clusterWorkerContext, localWorkerContext);
    }

    @Test
    public void testEsIndex() {
        Assert.assertEquals(SegmentIndex.Index, segmentSave.esIndex());
    }

    @Test
    public void testEsType() {
        Assert.assertEquals(SegmentIndex.Type_Record, segmentSave.esType());
    }

    @Test
    public void testRole() {
        Assert.assertEquals(SegmentSave.class.getSimpleName(), SegmentSave.Role.INSTANCE.roleName());
        Assert.assertEquals(RollingSelector.class.getSimpleName(), SegmentSave.Role.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        Assert.assertEquals(SegmentSave.class.getSimpleName(), SegmentSave.Factory.INSTANCE.role().roleName());
        Assert.assertEquals(SegmentSave.class.getSimpleName(), SegmentSave.Factory.INSTANCE.workerInstance(null).getClass().getSimpleName());

        int testSize = 10;
        WorkerConfig.Queue.Segment.SegmentSave.Size = testSize;
        Assert.assertEquals(testSize, SegmentSave.Factory.INSTANCE.queueSize());
    }

    @Test
    public void testAnalyse() throws Exception {
        WorkerConfig.Persistence.Data.size = 1;

        JsonObject segment_1 = new JsonObject();
        segment_1.addProperty("ts", "segment_1");
        segmentSave.analyse(segment_1);

        Assert.assertEquals("segment_1", saveToEsSource.ts);
    }

    class SaveToEsSource implements Answer<Object> {

        String ts = "";

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            Gson gson = new Gson();
            String source = (String) invocation.getArguments()[0];
            JsonObject sourceJsonObj = gson.fromJson(source, JsonObject.class);
            ts = sourceJsonObj.get("ts").getAsString();
            return null;
        }
    }
}
