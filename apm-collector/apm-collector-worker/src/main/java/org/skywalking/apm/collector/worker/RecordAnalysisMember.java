package org.skywalking.apm.collector.worker;

import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.Role;
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

    final public void set(String id, JsonObject record) throws Exception {
        getRecordAnalysisData().getOrCreate(id).set(record);
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
                logger.error(e, e);
            }
        });
        getRecordAnalysisData().asMap().clear();
    }

    protected abstract WorkerRefs aggWorkRefs();
}
