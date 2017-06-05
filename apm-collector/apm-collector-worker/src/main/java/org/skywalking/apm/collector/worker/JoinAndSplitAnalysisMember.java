package org.skywalking.apm.collector.worker;

import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.Role;
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

    final protected void set(String id, String attributeName, String value) throws Exception {
        getJoinAndSplitAnalysisData().getOrCreate(id).set(attributeName, value);
    }

    @Override
    final protected void aggregation() throws Exception {
        getJoinAndSplitAnalysisData().asMap().forEach((key, value) -> {
            try {
                aggWorkRefs().tell(value);
            } catch (Exception e) {
                logger().error(e);
            }
        });
        getJoinAndSplitAnalysisData().asMap().clear();
    }

    protected abstract WorkerRefs aggWorkRefs();
}
