package org.skywalking.apm.collector.worker;

import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.Role;
import org.skywalking.apm.collector.actor.WorkerInvokeException;
import org.skywalking.apm.collector.actor.WorkerRefs;
import org.skywalking.apm.collector.worker.storage.RecordAnalysisData;

/**
 * @author pengys5
 */
public abstract class RecordAnalysisMember extends AnalysisMember {

    private Logger logger = LogManager.getFormatterLogger(RecordAnalysisMember.class);

    private RecordAnalysisData recordAnalysisData = new RecordAnalysisData();

    public RecordAnalysisMember(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    final public void set(String id, JsonObject record) {
        getRecordAnalysisData().getOrCreate(id).set(record);
    }

    private RecordAnalysisData getRecordAnalysisData() {
        return recordAnalysisData;
    }

    @Override final protected void aggregation() {
        getRecordAnalysisData().asMap().forEach((key, value) -> {
            try {
                aggWorkRefs().tell(value);
            } catch (WorkerInvokeException e) {
                logger.error(e.getMessage(), e);
            }
        });
        getRecordAnalysisData().asMap().clear();
    }

    protected abstract WorkerRefs aggWorkRefs();
}
