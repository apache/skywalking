package com.a.eye.skywalking.collector.worker;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.Role;
import com.a.eye.skywalking.collector.worker.config.CacheSizeConfig;
import com.a.eye.skywalking.collector.worker.storage.MetricData;
import com.a.eye.skywalking.collector.worker.storage.MetricPersistenceData;

/**
 * @author pengys5
 */
public abstract class MetricAnalysisMember extends AnalysisMember {

    private MetricPersistenceData persistenceData = new MetricPersistenceData();

    public MetricAnalysisMember(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    final protected void setMetric(String id, String column, Long value) throws Exception {
        persistenceData.getElseCreate(id).setMetric(column, value);
        if (persistenceData.size() >= CacheSizeConfig.Cache.Persistence.SIZE) {
            aggregation();
        }
    }

    final public MetricData pushOne() {
        if (persistenceData.iterator().hasNext()) {
            return persistenceData.pushOne();
        }
        return null;
    }
}
