package com.a.eye.skywalking.collector.worker;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.Role;
import com.a.eye.skywalking.collector.actor.WorkerRefs;
import com.a.eye.skywalking.collector.worker.storage.RecordAnalysisData;
import com.google.gson.JsonObject;

/**
 * @author pengys5
 */
public abstract class RecordAnalysisMember extends AnalysisMember {

    private RecordAnalysisData recordAnalysisData = new RecordAnalysisData();

    public RecordAnalysisMember(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    final public void setRecord(String id, JsonObject record) throws Exception {
        getRecordAnalysisData().getOrCreate(id).setRecord(record);
    }

    private RecordAnalysisData getRecordAnalysisData() {
        return recordAnalysisData;
    }

    @Override
    final protected void aggregation() throws Exception {
        getRecordAnalysisData().asMap().forEach((key, value) -> {
            try {
                aggWorkRefs().tell(value);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        getRecordAnalysisData().asMap().clear();
    }

    protected abstract WorkerRefs aggWorkRefs();
}
