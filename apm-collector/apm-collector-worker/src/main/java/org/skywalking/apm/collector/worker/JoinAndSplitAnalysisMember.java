package org.skywalking.apm.collector.worker;

import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.Role;
import org.skywalking.apm.collector.actor.WorkerInvokeException;
import org.skywalking.apm.collector.actor.WorkerRefs;
import org.skywalking.apm.collector.worker.storage.JoinAndSplitAnalysisData;

/**
 * @author pengys5
 */
public abstract class JoinAndSplitAnalysisMember extends AnalysisMember {
    private JoinAndSplitAnalysisData joinAndSplitAnalysisData;

    public JoinAndSplitAnalysisMember(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
        joinAndSplitAnalysisData = new JoinAndSplitAnalysisData();
    }

    private JoinAndSplitAnalysisData getJoinAndSplitAnalysisData() {
        return joinAndSplitAnalysisData;
    }

    final protected void set(String id, String attributeName, String value) {
        getJoinAndSplitAnalysisData().getOrCreate(id).set(attributeName, value);
    }

    @Override final protected void aggregation() {
        getJoinAndSplitAnalysisData().asMap().forEach((key, value) -> {
            try {
                aggWorkRefs().tell(value);
            } catch (WorkerInvokeException e) {
                logger().error(e.getMessage(), e);
            }
        });
        getJoinAndSplitAnalysisData().asMap().clear();
    }

    protected abstract WorkerRefs aggWorkRefs();
}
