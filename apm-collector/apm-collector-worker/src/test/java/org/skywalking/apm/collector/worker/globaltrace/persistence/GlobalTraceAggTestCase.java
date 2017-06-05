package org.skywalking.apm.collector.worker.globaltrace.persistence;

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
import org.skywalking.apm.collector.worker.mock.MergeDataAnswer;
import org.skywalking.apm.collector.worker.storage.RecordData;
import org.skywalking.apm.collector.worker.tools.MergeDataAggTools;

import java.util.TimeZone;

import static org.mockito.Mockito.*;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest( {LocalWorkerContext.class})
@PowerMockIgnore( {"javax.management.*"})
public class GlobalTraceAggTestCase {

    private GlobalTraceAgg agg;
    private MergeDataAnswer mergeDataAnswer;
    private ClusterWorkerContext clusterWorkerContext;

    @Before
    public void init() throws Exception {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        clusterWorkerContext = PowerMockito.mock(ClusterWorkerContext.class);

        LocalWorkerContext localWorkerContext = PowerMockito.mock(LocalWorkerContext.class);
        WorkerRefs workerRefs = mock(WorkerRefs.class);

        mergeDataAnswer = new MergeDataAnswer();
        doAnswer(mergeDataAnswer).when(workerRefs).tell(Mockito.any(RecordData.class));

        when(localWorkerContext.lookup(GlobalTraceSave.Role.INSTANCE)).thenReturn(workerRefs);
        agg = new GlobalTraceAgg(GlobalTraceAgg.Role.INSTANCE, clusterWorkerContext, localWorkerContext);
    }

    @Test
    public void testRole() {
        Assert.assertEquals(GlobalTraceAgg.class.getSimpleName(), GlobalTraceAgg.Role.INSTANCE.roleName());
        Assert.assertEquals(HashCodeSelector.class.getSimpleName(), GlobalTraceAgg.Role.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        GlobalTraceAgg.Factory factory = new GlobalTraceAgg.Factory();
        Assert.assertEquals(GlobalTraceAgg.class.getSimpleName(), factory.role().roleName());
        Assert.assertEquals(GlobalTraceAgg.class.getSimpleName(), factory.workerInstance(null).getClass().getSimpleName());

        int testSize = 10;
        WorkerConfig.WorkerNum.GlobalTrace.GlobalTraceAgg.VALUE = testSize;
        Assert.assertEquals(testSize, factory.workerNum());
    }

    @Test
    public void testPreStart() throws ProviderNotFoundException {
        when(clusterWorkerContext.findProvider(GlobalTraceSave.Role.INSTANCE)).thenReturn(new GlobalTraceSave.Factory());

        ArgumentCaptor<GlobalTraceSave.Role> argumentCaptor = ArgumentCaptor.forClass(GlobalTraceSave.Role.class);
        agg.preStart();
        verify(clusterWorkerContext).findProvider(argumentCaptor.capture());
    }

    @Test
    public void testOnWork() throws Exception {
        MergeDataAggTools.INSTANCE.testOnWork(agg, mergeDataAnswer);
    }

    @Test
    public void testOnWorkError() throws Exception {
        agg.onWork(new Object());
    }
}
