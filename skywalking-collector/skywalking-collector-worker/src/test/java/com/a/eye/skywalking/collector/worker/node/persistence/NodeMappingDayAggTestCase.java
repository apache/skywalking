package com.a.eye.skywalking.collector.worker.node.persistence;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.ProviderNotFoundException;
import com.a.eye.skywalking.collector.actor.WorkerRefs;
import com.a.eye.skywalking.collector.actor.selector.HashCodeSelector;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.mock.RecordDataAnswer;
import com.a.eye.skywalking.collector.worker.storage.RecordData;
import com.a.eye.skywalking.collector.worker.tools.RecordDataAggTools;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.TimeZone;

import static org.mockito.Mockito.*;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({LocalWorkerContext.class})
@PowerMockIgnore({"javax.management.*"})
public class NodeMappingDayAggTestCase {

    private NodeMappingDayAgg agg;
    private RecordDataAnswer recordDataAnswer;
    private ClusterWorkerContext clusterWorkerContext;

    @Before
    public void init() throws Exception {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        clusterWorkerContext = PowerMockito.mock(ClusterWorkerContext.class);

        LocalWorkerContext localWorkerContext = PowerMockito.mock(LocalWorkerContext.class);
        WorkerRefs workerRefs = mock(WorkerRefs.class);

        recordDataAnswer = new RecordDataAnswer();
        doAnswer(recordDataAnswer).when(workerRefs).tell(Mockito.any(RecordData.class));

        when(localWorkerContext.lookup(NodeMappingDaySave.Role.INSTANCE)).thenReturn(workerRefs);
        agg = new NodeMappingDayAgg(NodeMappingDayAgg.Role.INSTANCE, clusterWorkerContext, localWorkerContext);
    }

    @Test
    public void testRole() {
        Assert.assertEquals(NodeMappingDayAgg.class.getSimpleName(), NodeMappingDayAgg.Role.INSTANCE.roleName());
        Assert.assertEquals(HashCodeSelector.class.getSimpleName(), NodeMappingDayAgg.Role.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        Assert.assertEquals(NodeMappingDayAgg.class.getSimpleName(), NodeMappingDayAgg.Factory.INSTANCE.role().roleName());
        Assert.assertEquals(NodeMappingDayAgg.class.getSimpleName(), NodeMappingDayAgg.Factory.INSTANCE.workerInstance(null).getClass().getSimpleName());

        int testSize = 10;
        WorkerConfig.WorkerNum.Node.NodeMappingDayAgg.Value = testSize;
        Assert.assertEquals(testSize, NodeMappingDayAgg.Factory.INSTANCE.workerNum());
    }

    @Test
    public void testPreStart() throws ProviderNotFoundException {
        when(clusterWorkerContext.findProvider(NodeMappingDaySave.Role.INSTANCE)).thenReturn(NodeMappingDaySave.Factory.INSTANCE);

        ArgumentCaptor<NodeMappingDaySave.Role> argumentCaptor = ArgumentCaptor.forClass(NodeMappingDaySave.Role.class);
        agg.preStart();
        verify(clusterWorkerContext).findProvider(argumentCaptor.capture());
    }

    @Test
    public void testOnWork() throws Exception {
        RecordDataAggTools.INSTANCE.testOnWork(agg, recordDataAnswer);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOnWorkError() throws Exception {
        agg.onWork(new Object());
    }
}
