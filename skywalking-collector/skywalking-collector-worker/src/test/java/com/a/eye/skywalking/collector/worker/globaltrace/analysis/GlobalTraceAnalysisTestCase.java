package com.a.eye.skywalking.collector.worker.globaltrace.analysis;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.WorkerRefs;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.worker.config.WorkerConfig;
import com.a.eye.skywalking.collector.worker.globaltrace.persistence.GlobalTraceAgg;
import com.a.eye.skywalking.collector.worker.mock.MergeDataAnswer;
import com.a.eye.skywalking.collector.worker.segment.mock.SegmentMock;
import com.a.eye.skywalking.collector.worker.storage.MergeData;
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
public class GlobalTraceAnalysisTestCase {

    private GlobalTraceAnalysis analysis;
    private SegmentMock segmentMock = new SegmentMock();
    private MergeDataAnswer answer;
    private ClusterWorkerContext clusterWorkerContext;

    @Before
    public void init() throws Exception {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        clusterWorkerContext = PowerMockito.mock(ClusterWorkerContext.class);
        WorkerRefs workerRefs = mock(WorkerRefs.class);
        answer = new MergeDataAnswer();
        doAnswer(answer).when(workerRefs).tell(Mockito.any(MergeData.class));

        when(clusterWorkerContext.lookup(GlobalTraceAgg.Role.INSTANCE)).thenReturn(workerRefs);

        LocalWorkerContext localWorkerContext = new LocalWorkerContext();
        analysis = new GlobalTraceAnalysis(GlobalTraceAnalysis.Role.INSTANCE, clusterWorkerContext, localWorkerContext);
    }

    @Test
    public void testRole() {
        Assert.assertEquals(GlobalTraceAnalysis.class.getSimpleName(), GlobalTraceAnalysis.Role.INSTANCE.roleName());
        Assert.assertEquals(RollingSelector.class.getSimpleName(), GlobalTraceAnalysis.Role.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        Assert.assertEquals(GlobalTraceAnalysis.class.getSimpleName(), GlobalTraceAnalysis.Factory.INSTANCE.role().roleName());
        Assert.assertEquals(GlobalTraceAnalysis.class.getSimpleName(), GlobalTraceAnalysis.Factory.INSTANCE.workerInstance(null).getClass().getSimpleName());

        int testSize = 10;
        WorkerConfig.Queue.GlobalTrace.GlobalTraceAnalysis.SIZE = testSize;
        Assert.assertEquals(testSize, GlobalTraceAnalysis.Factory.INSTANCE.queueSize());
    }

    @Test
    public void testAnalyse() throws Exception {
        segmentMock.executeAnalysis(analysis);

        Assert.assertEquals(1, answer.getMergeDataList().size());
        MergeData mergeData = answer.getMergeDataList().get(0);
        Assert.assertEquals(id, mergeData.getId());
        String subSegIds = mergeData.toMap().get("subSegIds");
        Assert.assertEquals(cacheServiceSubSegIds, subSegIds);
    }

    private String id = "Trace.1490922929254.1797892356.6003.69.2";
    private String cacheServiceSubSegIds = "Segment.1490922929298.927784221.5991.28.1,Segment.1490922929274.1382198130.5997.47.1,Segment.1490922929258.927784221.5991.27.1,Segment.1490922929254.1797892356.6003.69.1";
}
