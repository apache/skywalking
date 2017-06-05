package org.skywalking.apm.collector.worker.noderef.persistence;

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
import org.skywalking.apm.collector.worker.mock.MetricDataAnswer;
import org.skywalking.apm.collector.worker.storage.MetricData;
import org.skywalking.apm.collector.worker.tools.MetricDataAggTools;

import java.util.TimeZone;

import static org.mockito.Mockito.*;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest( {LocalWorkerContext.class})
@PowerMockIgnore( {"javax.management.*"})
public class NodeRefResSumHourAggTestCase {

    private NodeRefResSumHourAgg agg;
    private MetricDataAnswer metricDataAnswer;
    private ClusterWorkerContext clusterWorkerContext;
    private LocalWorkerContext localWorkerContext;

    @Before
    public void init() throws Exception {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        clusterWorkerContext = PowerMockito.mock(ClusterWorkerContext.class);

        localWorkerContext = PowerMockito.mock(LocalWorkerContext.class);
        WorkerRefs workerRefs = mock(WorkerRefs.class);

        metricDataAnswer = new MetricDataAnswer();
        doAnswer(metricDataAnswer).when(workerRefs).tell(Mockito.any(MetricData.class));

        when(localWorkerContext.lookup(NodeRefResSumHourSave.Role.INSTANCE)).thenReturn(workerRefs);
        agg = new NodeRefResSumHourAgg(NodeRefResSumHourAgg.Role.INSTANCE, clusterWorkerContext, localWorkerContext);
    }

    @Test
    public void testRole() {
        Assert.assertEquals(NodeRefResSumHourAgg.class.getSimpleName(), NodeRefResSumHourAgg.Role.INSTANCE.roleName());
        Assert.assertEquals(HashCodeSelector.class.getSimpleName(), NodeRefResSumHourAgg.Role.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        NodeRefResSumHourAgg.Factory factory = new NodeRefResSumHourAgg.Factory();
        Assert.assertEquals(NodeRefResSumHourAgg.class.getSimpleName(), factory.role().roleName());
        Assert.assertEquals(NodeRefResSumHourAgg.class.getSimpleName(), factory.workerInstance(null).getClass().getSimpleName());

        int testSize = 10;
        WorkerConfig.WorkerNum.NodeRef.NodeRefResSumHourAgg.VALUE = testSize;
        Assert.assertEquals(testSize, factory.workerNum());
    }

    @Test
    public void testPreStart() throws ProviderNotFoundException {
        when(clusterWorkerContext.findProvider(NodeRefResSumHourSave.Role.INSTANCE)).thenReturn(new NodeRefResSumHourSave.Factory());

        ArgumentCaptor<NodeRefResSumHourSave.Role> argumentCaptor = ArgumentCaptor.forClass(NodeRefResSumHourSave.Role.class);
        agg.preStart();
        verify(clusterWorkerContext).findProvider(argumentCaptor.capture());
    }

    @Test
    public void testOnWork() throws Exception {
        MetricDataAggTools.INSTANCE.testOnWork(agg, metricDataAnswer);
    }
}
