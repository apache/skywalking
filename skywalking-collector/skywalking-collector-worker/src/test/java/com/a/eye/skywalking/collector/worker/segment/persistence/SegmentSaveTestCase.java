package com.a.eye.skywalking.collector.worker.segment.persistence;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.worker.config.CacheSizeConfig;
import com.a.eye.skywalking.collector.worker.mock.MockEsBulkClient;
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

import java.util.TimeZone;

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
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        mockEsBulkClient.createMock();

        saveToEsSource = new SaveToEsSource();
        when(mockEsBulkClient.indexRequestBuilder.setSource(Mockito.anyString())).thenAnswer(saveToEsSource);

        ClusterWorkerContext clusterWorkerContext = new ClusterWorkerContext(null);
        LocalWorkerContext localWorkerContext = new LocalWorkerContext();

        segmentSave = new SegmentSave(SegmentSave.Role.INSTANCE, clusterWorkerContext, localWorkerContext);
    }

    @Test
    public void testEsIndex() {
        Assert.assertEquals(SegmentIndex.INDEX, segmentSave.esIndex());
    }

    @Test
    public void testEsType() {
        Assert.assertEquals(SegmentIndex.TYPE_RECORD, segmentSave.esType());
    }

    @Test
    public void testRole() {
        Assert.assertEquals(SegmentSave.class.getSimpleName(), SegmentSave.Role.INSTANCE.roleName());
        Assert.assertEquals(RollingSelector.class.getSimpleName(), SegmentSave.Role.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        SegmentSave.Factory factory = new SegmentSave.Factory();
        Assert.assertEquals(SegmentSave.class.getSimpleName(), factory.role().roleName());
        Assert.assertEquals(SegmentSave.class.getSimpleName(), factory.workerInstance(null).getClass().getSimpleName());
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
