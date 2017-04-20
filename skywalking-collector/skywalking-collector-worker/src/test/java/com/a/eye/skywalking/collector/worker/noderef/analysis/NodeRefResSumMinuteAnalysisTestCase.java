package com.a.eye.skywalking.collector.worker.noderef.analysis;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.WorkerRefs;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.worker.config.WorkerConfig;
import com.a.eye.skywalking.collector.worker.mock.MetricDataAnswer;
import com.a.eye.skywalking.collector.worker.noderef.persistence.NodeRefResSumMinuteAgg;
import com.a.eye.skywalking.collector.worker.storage.MetricData;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.TimeZone;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ClusterWorkerContext.class})
@PowerMockIgnore({"javax.management.*"})
public class NodeRefResSumMinuteAnalysisTestCase {

    private NodeRefResSumMinuteAnalysis analysis;
    private MetricDataAnswer answer;

    @Before
    public void init() throws Exception {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        ClusterWorkerContext clusterWorkerContext = PowerMockito.mock(ClusterWorkerContext.class);
        WorkerRefs workerRefs = mock(WorkerRefs.class);
        answer = new MetricDataAnswer();
        doAnswer(answer).when(workerRefs).tell(Mockito.any(MetricData.class));

        when(clusterWorkerContext.lookup(NodeRefResSumMinuteAgg.Role.INSTANCE)).thenReturn(workerRefs);

        LocalWorkerContext localWorkerContext = PowerMockito.mock(LocalWorkerContext.class);
        analysis = new NodeRefResSumMinuteAnalysis(NodeRefResSumMinuteAnalysis.Role.INSTANCE, clusterWorkerContext, localWorkerContext);
    }

    @Test
    public void testRole() {
        Assert.assertEquals(NodeRefResSumMinuteAnalysis.class.getSimpleName(), NodeRefResSumMinuteAnalysis.Role.INSTANCE.roleName());
        Assert.assertEquals(RollingSelector.class.getSimpleName(), NodeRefResSumMinuteAnalysis.Role.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        Assert.assertEquals(NodeRefResSumMinuteAnalysis.class.getSimpleName(), NodeRefResSumMinuteAnalysis.Factory.INSTANCE.role().roleName());
        Assert.assertEquals(NodeRefResSumMinuteAnalysis.class.getSimpleName(), NodeRefResSumMinuteAnalysis.Factory.INSTANCE.workerInstance(null).getClass().getSimpleName());

        int testSize = 10;
        WorkerConfig.Queue.NodeRef.NodeRefResSumMinuteAnalysis.SIZE = testSize;
        Assert.assertEquals(testSize, NodeRefResSumMinuteAnalysis.Factory.INSTANCE.queueSize());
    }

    String jsonFile = "/json/noderef/analysis/noderef_ressum_minute_analysis.json";
    String requestJsonFile = "/json/noderef/analysis/noderef_ressum_minute_analysis_request.json";

    @Test
    public void testAnalyse() throws Exception {
        NodeRefResSumAnalyse.INSTANCE.analyse(requestJsonFile, jsonFile, analysis, answer);
    }
}
