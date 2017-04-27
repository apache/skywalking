package com.a.eye.skywalking.collector.worker.segment.persistence;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.worker.mock.MockEsBulkClient;
import com.a.eye.skywalking.collector.worker.mock.SaveToEsSourceAnswer;
import com.a.eye.skywalking.collector.worker.segment.SegmentCostIndex;
import com.a.eye.skywalking.collector.worker.segment.mock.SegmentMock;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
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
        SegmentCostSave.Factory factory = new SegmentCostSave.Factory();
        Assert.assertEquals(SegmentCostSave.class.getSimpleName(), factory.role().roleName());
        Assert.assertEquals(SegmentCostSave.class.getSimpleName(), factory.workerInstance(null).getClass().getSimpleName());
    }
}
