package com.a.eye.skywalking.collector.worker.noderef.analysis;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.WorkerRefs;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.worker.Const;
import com.a.eye.skywalking.collector.worker.storage.MetricAnalysisData;
import java.lang.reflect.Field;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author pengys5
 */
public class AbstractNodeRefResSumAnalysisTestCase {

    @Test
    public void analyseResSum() throws Exception {
        Impl impl = new Impl(Role.INSTANCE, null, null);

        AbstractNodeRefResSumAnalysis.NodeRefResRecord record =
            new AbstractNodeRefResSumAnalysis.NodeRefResRecord(1, 2, 3, 4);
        record.setStartTime(10);
        record.setEndTime(20);
        record.setError(false);

        String id = 2017 + Const.ID_SPLIT + "A" + Const.ID_SPLIT + "B";
        record.setNodeRefId(id);
        Assert.assertEquals(id, record.getNodeRefId());

        impl.analyseResSum(record);

        record.setStartTime(0);
        record.setEndTime(2000);
        record.setError(false);
        impl.analyseResSum(record);

        record.setStartTime(0);
        record.setEndTime(4000);
        record.setError(false);
        impl.analyseResSum(record);

        record.setStartTime(0);
        record.setEndTime(6000);
        record.setError(false);
        impl.analyseResSum(record);

        record.setStartTime(0);
        record.setEndTime(6000);
        record.setError(true);
        impl.analyseResSum(record);

        Field testAField = impl.getClass().getSuperclass().getSuperclass().getDeclaredField("metricAnalysisData");
        testAField.setAccessible(true);

        MetricAnalysisData metricAnalysisData = (MetricAnalysisData)testAField.get(impl);

        Assert.assertEquals(1L, metricAnalysisData.asMap().get("2017..-..A..-..B").asMap().get("oneSecondLess"));
        Assert.assertEquals(1L, metricAnalysisData.asMap().get("2017..-..A..-..B").asMap().get("threeSecondLess"));
        Assert.assertEquals(1L, metricAnalysisData.asMap().get("2017..-..A..-..B").asMap().get("fiveSecondLess"));
        Assert.assertEquals(1L, metricAnalysisData.asMap().get("2017..-..A..-..B").asMap().get("fiveSecondGreater"));
        Assert.assertEquals(1L, metricAnalysisData.asMap().get("2017..-..A..-..B").asMap().get("error"));
        Assert.assertEquals(5L, metricAnalysisData.asMap().get("2017..-..A..-..B").asMap().get("summary"));
    }

    class Impl extends AbstractNodeRefResSumAnalysis {
        Impl(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
            super(role, clusterContext, selfContext);
        }

        @Override public void analyse(Object message) throws Exception {

        }

        @Override protected WorkerRefs aggWorkRefs() {
            return null;
        }
    }

    enum Role implements com.a.eye.skywalking.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return Impl.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
