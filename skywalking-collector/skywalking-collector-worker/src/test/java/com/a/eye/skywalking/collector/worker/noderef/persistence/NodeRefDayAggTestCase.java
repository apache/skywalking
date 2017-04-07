package com.a.eye.skywalking.collector.worker.noderef.persistence;

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

import static org.mockito.Mockito.*;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({LocalWorkerContext.class})
@PowerMockIgnore({"javax.management.*"})
public class NodeRefDayAggTestCase {

    private NodeRefDayAgg agg;
    private RecordDataAnswer recordDataAnswer;
    private ClusterWorkerContext clusterWorkerContext;
    private LocalWorkerContext localWorkerContext;

    @Before
    public void init() throws Exception {
        clusterWorkerContext = PowerMockito.mock(ClusterWorkerContext.class);

        localWorkerContext = PowerMockito.mock(LocalWorkerContext.class);
        WorkerRefs workerRefs = mock(WorkerRefs.class);

        recordDataAnswer = new RecordDataAnswer();
        doAnswer(recordDataAnswer).when(workerRefs).tell(Mockito.any(RecordData.class));

        when(localWorkerContext.lookup(NodeRefDaySave.Role.INSTANCE)).thenReturn(workerRefs);
        agg = new NodeRefDayAgg(NodeRefDayAgg.Role.INSTANCE, clusterWorkerContext, localWorkerContext);
    }

    @Test
    public void testRole() {
        Assert.assertEquals(NodeRefDayAgg.class.getSimpleName(), NodeRefDayAgg.Role.INSTANCE.roleName());
        Assert.assertEquals(HashCodeSelector.class.getSimpleName(), NodeRefDayAgg.Role.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        Assert.assertEquals(NodeRefDayAgg.class.getSimpleName(), NodeRefDayAgg.Factory.INSTANCE.role().roleName());
        Assert.assertEquals(NodeRefDayAgg.class.getSimpleName(), NodeRefDayAgg.Factory.INSTANCE.workerInstance(null).getClass().getSimpleName());

        int testSize = 10;
        WorkerConfig.WorkerNum.NodeRef.NodeRefDayAgg.Value = testSize;
        Assert.assertEquals(testSize, NodeRefDayAgg.Factory.INSTANCE.workerNum());
    }

    @Test
    public void testPreStart() throws ProviderNotFoundException {
        when(clusterWorkerContext.findProvider(NodeRefDaySave.Role.INSTANCE)).thenReturn(NodeRefDaySave.Factory.INSTANCE);

        ArgumentCaptor<NodeRefDaySave.Role> argumentCaptor = ArgumentCaptor.forClass(NodeRefDaySave.Role.class);
        agg.preStart();
        verify(clusterWorkerContext).findProvider(argumentCaptor.capture());
    }

    @Test
    public void testOnWorkError() throws Exception {
        agg.onWork(new Object());
    }

    @Test
    public void testOnWork() throws Exception {
        RecordDataAggTools.INSTANCE.testOnWork(agg, recordDataAnswer);
    }
}
