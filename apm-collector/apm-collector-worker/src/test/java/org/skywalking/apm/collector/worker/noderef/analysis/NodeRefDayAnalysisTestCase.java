package org.skywalking.apm.collector.worker.noderef.analysis;

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
import org.skywalking.apm.collector.actor.selector.RollingSelector;
import org.skywalking.apm.collector.worker.config.WorkerConfig;
import org.skywalking.apm.collector.worker.mock.RecordDataAnswer;
import org.skywalking.apm.collector.worker.noderef.persistence.NodeRefDayAgg;
import org.skywalking.apm.collector.worker.storage.RecordData;

import java.util.TimeZone;

import static org.mockito.Mockito.*;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest( {ClusterWorkerContext.class})
@PowerMockIgnore( {"javax.management.*"})
public class NodeRefDayAnalysisTestCase {

    private NodeRefDayAnalysis analysis;
    private RecordDataAnswer answer;
    private ClusterWorkerContext clusterWorkerContext;
    private NodeRefResRecordAnswer recordAnswer;

    @Before
    public void init() throws Exception {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        clusterWorkerContext = PowerMockito.mock(ClusterWorkerContext.class);
        WorkerRefs workerRefs = mock(WorkerRefs.class);
        answer = new RecordDataAnswer();
        doAnswer(answer).when(workerRefs).tell(Mockito.any(RecordData.class));

        when(clusterWorkerContext.lookup(NodeRefDayAgg.Role.INSTANCE)).thenReturn(workerRefs);

        LocalWorkerContext localWorkerContext = PowerMockito.mock(LocalWorkerContext.class);

        WorkerRefs nodeRefResSumWorkerRefs = mock(WorkerRefs.class);
        recordAnswer = new NodeRefResRecordAnswer();
        doAnswer(recordAnswer).when(nodeRefResSumWorkerRefs).tell(Mockito.any(AbstractNodeRefResSumAnalysis.NodeRefResRecord.class));

        when(localWorkerContext.lookup(NodeRefResSumDayAnalysis.Role.INSTANCE)).thenReturn(nodeRefResSumWorkerRefs);
        analysis = new NodeRefDayAnalysis(NodeRefDayAnalysis.Role.INSTANCE, clusterWorkerContext, localWorkerContext);
    }

    @Test
    public void testRole() {
        Assert.assertEquals(NodeRefDayAnalysis.class.getSimpleName(), NodeRefDayAnalysis.Role.INSTANCE.roleName());
        Assert.assertEquals(RollingSelector.class.getSimpleName(), NodeRefDayAnalysis.Role.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        NodeRefDayAnalysis.Factory factory = new NodeRefDayAnalysis.Factory();
        Assert.assertEquals(NodeRefDayAnalysis.class.getSimpleName(), factory.role().roleName());
        Assert.assertEquals(NodeRefDayAnalysis.class.getSimpleName(), factory.workerInstance(null).getClass().getSimpleName());

        int testSize = 10;
        WorkerConfig.Queue.NodeRef.NodeRefDayAnalysis.SIZE = testSize;
        Assert.assertEquals(testSize, factory.queueSize());
    }

    String jsonFile = "/json/noderef/analysis/noderef_day_analysis.json";
    String resSumJsonFile = "/json/noderef/analysis/noderef_ressum_day_analysis_request.json";

    @Test
    public void testAnalyse() throws Exception {
        NodeRefAnalyse.INSTANCE.analyse(resSumJsonFile, jsonFile, analysis, answer, recordAnswer);
    }

    @Test
    public void testPreStart() throws ProviderNotFoundException {
        Mockito.when(clusterWorkerContext.findProvider(NodeRefResSumDayAnalysis.Role.INSTANCE)).thenReturn(new NodeRefResSumDayAnalysis.Factory());

        ArgumentCaptor<NodeRefResSumDayAnalysis.Role> argumentCaptor = ArgumentCaptor.forClass(NodeRefResSumDayAnalysis.Role.class);
        analysis.preStart();
        verify(clusterWorkerContext).findProvider(argumentCaptor.capture());
    }
}
