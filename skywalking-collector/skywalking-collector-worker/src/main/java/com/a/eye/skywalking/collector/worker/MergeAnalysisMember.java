package com.a.eye.skywalking.collector.worker;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.Role;
import com.a.eye.skywalking.collector.worker.config.CacheSizeConfig;
import com.a.eye.skywalking.collector.worker.storage.MergeData;
import com.a.eye.skywalking.collector.worker.storage.MergePersistenceData;

/**
 * @author pengys5
 */
public abstract class MergeAnalysisMember extends AnalysisMember {

    private MergePersistenceData persistenceData;

    protected MergeAnalysisMember(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
        persistenceData = new MergePersistenceData();
    }

    private MergePersistenceData getPersistenceData() {
        return persistenceData;
    }

    final protected void setMergeData(String id, String column, String value) throws Exception {
        getPersistenceData().getElseCreate(id).setMergeData(column, value);
        if (getPersistenceData().size() >= CacheSizeConfig.Cache.Analysis.SIZE) {
            aggregation();
        }
    }

    final public MergeData pushOne() {
        if (getPersistenceData().iterator().hasNext()) {
            return getPersistenceData().pushOne();
        }
        return null;
    }
}
