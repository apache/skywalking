package com.a.eye.skywalking.collector.worker.noderef.analysis;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.WorkerRefs;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.worker.config.WorkerConfig;
import com.a.eye.skywalking.collector.worker.mock.MetricDataAnswer;
import com.a.eye.skywalking.collector.worker.noderef.persistence.NodeRefResSumHourAgg;
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
public class NodeRefResSumHourAnalysisTestCase {

    private NodeRefResSumHourAnalysis analysis;
    private MetricDataAnswer answer;

    @Before
    public void init() throws Exception {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        ClusterWorkerContext clusterWorkerContext = PowerMockito.mock(ClusterWorkerContext.class);
        WorkerRefs workerRefs = mock(WorkerRefs.class);
        answer = new MetricDataAnswer();
        doAnswer(answer).when(workerRefs).tell(Mockito.any(MetricData.class));

        when(clusterWorkerContext.lookup(NodeRefResSumHourAgg.Role.INSTANCE)).thenReturn(workerRefs);

        LocalWorkerContext localWorkerContext = PowerMockito.mock(LocalWorkerContext.class);
        analysis = new NodeRefResSumHourAnalysis(NodeRefResSumHourAnalysis.Role.INSTANCE, clusterWorkerContext, localWorkerContext);
    }

    @Test
    public void testRole() {
        Assert.assertEquals(NodeRefResSumHourAnalysis.class.getSimpleName(), NodeRefResSumHourAnalysis.Role.INSTANCE.roleName());
        Assert.assertEquals(RollingSelector.class.getSimpleName(), NodeRefResSumHourAnalysis.Role.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        NodeRefResSumHourAnalysis.Factory factory = new NodeRefResSumHourAnalysis.Factory();
        Assert.assertEquals(NodeRefResSumHourAnalysis.class.getSimpleName(), factory.role().roleName());
        Assert.assertEquals(NodeRefResSumHourAnalysis.class.getSimpleName(), factory.workerInstance(null).getClass().getSimpleName());

        int testSize = 10;
        WorkerConfig.Queue.NodeRef.NodeRefResSumHourAnalysis.SIZE = testSize;
        Assert.assertEquals(testSize, factory.queueSize());
    }

    String jsonFile = "/json/noderef/analysis/noderef_ressum_hour_analysis.json";
    String requestJsonFile = "/json/noderef/analysis/noderef_ressum_hour_analysis_request.json";

    @Test
    public void testAnalyse() throws Exception {
        NodeRefResSumAnalyse.INSTANCE.analyse(requestJsonFile, jsonFile, analysis, answer);
    }
}
