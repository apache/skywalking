package com.a.eye.skywalking.collector.worker.noderef.analysis;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.ProviderNotFoundException;
import com.a.eye.skywalking.collector.actor.WorkerRefs;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.worker.config.WorkerConfig;
import com.a.eye.skywalking.collector.worker.mock.RecordDataAnswer;
import com.a.eye.skywalking.collector.worker.noderef.persistence.NodeRefMinuteAgg;
import com.a.eye.skywalking.collector.worker.storage.RecordData;
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
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ClusterWorkerContext.class})
@PowerMockIgnore({"javax.management.*"})
public class NodeRefMinuteAnalysisTestCase {

    private NodeRefMinuteAnalysis analysis;
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

        when(clusterWorkerContext.lookup(NodeRefMinuteAgg.Role.INSTANCE)).thenReturn(workerRefs);

        LocalWorkerContext localWorkerContext = PowerMockito.mock(LocalWorkerContext.class);

        WorkerRefs nodeRefResSumWorkerRefs = mock(WorkerRefs.class);
        recordAnswer = new NodeRefResRecordAnswer();
        doAnswer(recordAnswer).when(nodeRefResSumWorkerRefs).tell(Mockito.any(AbstractNodeRefResSumAnalysis.NodeRefResRecord.class));

        when(localWorkerContext.lookup(NodeRefResSumMinuteAnalysis.Role.INSTANCE)).thenReturn(nodeRefResSumWorkerRefs);
        analysis = new NodeRefMinuteAnalysis(NodeRefMinuteAnalysis.Role.INSTANCE, clusterWorkerContext, localWorkerContext);
    }

    @Test
    public void testRole() {
        Assert.assertEquals(NodeRefMinuteAnalysis.class.getSimpleName(), NodeRefMinuteAnalysis.Role.INSTANCE.roleName());
        Assert.assertEquals(RollingSelector.class.getSimpleName(), NodeRefMinuteAnalysis.Role.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        Assert.assertEquals(NodeRefMinuteAnalysis.class.getSimpleName(), NodeRefMinuteAnalysis.Factory.INSTANCE.role().roleName());
        Assert.assertEquals(NodeRefMinuteAnalysis.class.getSimpleName(), NodeRefMinuteAnalysis.Factory.INSTANCE.workerInstance(null).getClass().getSimpleName());

        int testSize = 10;
        WorkerConfig.Queue.NodeRef.NodeRefMinuteAnalysis.Size = testSize;
        Assert.assertEquals(testSize, NodeRefMinuteAnalysis.Factory.INSTANCE.queueSize());
    }

    String jsonFile = "/json/noderef/analysis/noderef_minute_analysis.json";
    String resSumJsonFile = "/json/noderef/analysis/noderef_ressum_minute_analysis_request.json";

    @Test
    public void testAnalyse() throws Exception {
        NodeRefAnalyse.INSTANCE.analyse(resSumJsonFile, jsonFile, analysis, answer, recordAnswer);
    }

    @Test
    public void testPreStart() throws ProviderNotFoundException {
        Mockito.when(clusterWorkerContext.findProvider(NodeRefResSumMinuteAnalysis.Role.INSTANCE)).thenReturn(NodeRefResSumMinuteAnalysis.Factory.INSTANCE);

        ArgumentCaptor<NodeRefResSumMinuteAnalysis.Role> argumentCaptor = ArgumentCaptor.forClass(NodeRefResSumMinuteAnalysis.Role.class);
        analysis.preStart();
        verify(clusterWorkerContext).findProvider(argumentCaptor.capture());
    }
}
