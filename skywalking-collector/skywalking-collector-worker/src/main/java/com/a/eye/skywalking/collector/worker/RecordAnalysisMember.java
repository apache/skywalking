package com.a.eye.skywalking.collector.worker;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.Role;
import com.a.eye.skywalking.collector.worker.config.CacheSizeConfig;
import com.a.eye.skywalking.collector.worker.storage.RecordData;
import com.a.eye.skywalking.collector.worker.storage.RecordPersistenceData;
import com.google.gson.JsonObject;

/**
 * @author pengys5
 */
public abstract class RecordAnalysisMember extends AnalysisMember {

    private RecordPersistenceData persistenceData = new RecordPersistenceData();

    public RecordAnalysisMember(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    final public void setRecord(String id, JsonObject record) throws Exception {
        persistenceData.getElseCreate(id).setRecord(record);
        if (persistenceData.size() >= CacheSizeConfig.Cache.Analysis.size) {
            aggregation();
        }
    }

    final public RecordData pushOne() {
        if (persistenceData.hasNext()) {
            return persistenceData.pushOne();
        }
        return null;
    }
}
