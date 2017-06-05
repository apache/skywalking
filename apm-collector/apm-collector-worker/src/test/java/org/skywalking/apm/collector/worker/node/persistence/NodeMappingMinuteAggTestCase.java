package org.skywalking.apm.collector.worker.node.persistence;

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
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.ProviderNotFoundException;
import org.skywalking.apm.collector.actor.WorkerRefs;
import org.skywalking.apm.collector.actor.selector.HashCodeSelector;
import org.skywalking.apm.collector.worker.config.WorkerConfig;
import org.skywalking.apm.collector.worker.mock.RecordDataAnswer;
import org.skywalking.apm.collector.worker.storage.RecordData;
import org.skywalking.apm.collector.worker.tools.RecordDataAggTools;

import java.util.TimeZone;

import static org.mockito.Mockito.*;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest( {LocalWorkerContext.class})
@PowerMockIgnore( {"javax.management.*"})
public class NodeMappingMinuteAggTestCase {

    private NodeMappingMinuteAgg agg;
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

        when(localWorkerContext.lookup(NodeMappingMinuteSave.Role.INSTANCE)).thenReturn(workerRefs);
        agg = new NodeMappingMinuteAgg(NodeMappingMinuteAgg.Role.INSTANCE, clusterWorkerContext, localWorkerContext);
    }

    @Test
    public void testRole() {
        Assert.assertEquals(NodeMappingMinuteAgg.class.getSimpleName(), NodeMappingMinuteAgg.Role.INSTANCE.roleName());
        Assert.assertEquals(HashCodeSelector.class.getSimpleName(), NodeMappingMinuteAgg.Role.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        NodeMappingMinuteAgg.Factory factory = new NodeMappingMinuteAgg.Factory();
        Assert.assertEquals(NodeMappingMinuteAgg.class.getSimpleName(), factory.role().roleName());
        Assert.assertEquals(NodeMappingMinuteAgg.class.getSimpleName(), factory.workerInstance(null).getClass().getSimpleName());

        int testSize = 10;
        WorkerConfig.WorkerNum.Node.NodeMappingMinuteAgg.VALUE = testSize;
        Assert.assertEquals(testSize, factory.workerNum());
    }

    @Test
    public void testPreStart() throws ProviderNotFoundException {
        when(clusterWorkerContext.findProvider(NodeMappingMinuteSave.Role.INSTANCE)).thenReturn(new NodeMappingMinuteSave.Factory());

        ArgumentCaptor<NodeMappingMinuteSave.Role> argumentCaptor = ArgumentCaptor.forClass(NodeMappingMinuteSave.Role.class);
        agg.preStart();
        verify(clusterWorkerContext).findProvider(argumentCaptor.capture());
    }

    @Test
    public void testOnWork() throws Exception {
        RecordDataAggTools.INSTANCE.testOnWork(agg, recordDataAnswer);
    }
}
