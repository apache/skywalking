package org.skywalking.apm.collector.worker;

import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.Role;
import org.skywalking.apm.collector.actor.WorkerRefs;
import org.skywalking.apm.collector.worker.storage.MetricAnalysisData;

/**
 * @author pengys5
 */
public abstract class MetricAnalysisMember extends AnalysisMember {
    private MetricAnalysisData metricAnalysisData = new MetricAnalysisData();

    public MetricAnalysisMember(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    final protected void set(String id, String metricName, Long value) {
        getMetricAnalysisData().getOrCreate(id).set(metricName, value);
    }

    private MetricAnalysisData getMetricAnalysisData() {
        return metricAnalysisData;
    }

    @Override final protected void aggregation() {
        getMetricAnalysisData().asMap().forEach((key, value) -> {
            try {
                aggWorkRefs().tell(value);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        getMetricAnalysisData().asMap().clear();
    }

    protected abstract WorkerRefs aggWorkRefs();
}
