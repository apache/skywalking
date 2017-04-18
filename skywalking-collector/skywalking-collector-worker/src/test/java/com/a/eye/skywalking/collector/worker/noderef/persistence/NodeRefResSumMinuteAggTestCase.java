package com.a.eye.skywalking.collector.worker.noderef.persistence;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.ProviderNotFoundException;
import com.a.eye.skywalking.collector.actor.WorkerRefs;
import com.a.eye.skywalking.collector.actor.selector.HashCodeSelector;
import com.a.eye.skywalking.collector.worker.config.WorkerConfig;
import com.a.eye.skywalking.collector.worker.mock.MetricDataAnswer;
import com.a.eye.skywalking.collector.worker.storage.MetricData;
import com.a.eye.skywalking.collector.worker.tools.MetricDataAggTools;
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
public class NodeRefResSumMinuteAggTestCase {

    private NodeRefResSumMinuteAgg agg;
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

        when(localWorkerContext.lookup(NodeRefResSumMinuteSave.Role.INSTANCE)).thenReturn(workerRefs);
        agg = new NodeRefResSumMinuteAgg(NodeRefResSumMinuteAgg.Role.INSTANCE, clusterWorkerContext, localWorkerContext);
    }

    @Test
    public void testRole() {
        Assert.assertEquals(NodeRefResSumMinuteAgg.class.getSimpleName(), NodeRefResSumMinuteAgg.Role.INSTANCE.roleName());
        Assert.assertEquals(HashCodeSelector.class.getSimpleName(), NodeRefResSumMinuteAgg.Role.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        Assert.assertEquals(NodeRefResSumMinuteAgg.class.getSimpleName(), NodeRefResSumMinuteAgg.Factory.INSTANCE.role().roleName());
        Assert.assertEquals(NodeRefResSumMinuteAgg.class.getSimpleName(), NodeRefResSumMinuteAgg.Factory.INSTANCE.workerInstance(null).getClass().getSimpleName());

        int testSize = 10;
        WorkerConfig.WorkerNum.NodeRef.NodeRefResSumMinuteAgg.VALUE = testSize;
        Assert.assertEquals(testSize, NodeRefResSumMinuteAgg.Factory.INSTANCE.workerNum());
    }

    @Test
    public void testPreStart() throws ProviderNotFoundException {
        when(clusterWorkerContext.findProvider(NodeRefResSumMinuteSave.Role.INSTANCE)).thenReturn(NodeRefResSumMinuteSave.Factory.INSTANCE);

        ArgumentCaptor<NodeRefResSumMinuteSave.Role> argumentCaptor = ArgumentCaptor.forClass(NodeRefResSumMinuteSave.Role.class);
        agg.preStart();
        verify(clusterWorkerContext).findProvider(argumentCaptor.capture());
    }

    @Test
    public void testOnWorkError() throws Exception {
        agg.onWork(new Object());
    }

    @Test
    public void testOnWork() throws Exception {
        MetricDataAggTools.INSTANCE.testOnWork(agg, metricDataAnswer);
    }
}
