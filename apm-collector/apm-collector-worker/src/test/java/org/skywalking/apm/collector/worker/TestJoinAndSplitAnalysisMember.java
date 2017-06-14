package org.skywalking.apm.collector.worker;

import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.ProviderNotFoundException;
import org.skywalking.apm.collector.actor.WorkerRefs;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;

/**
 * @author pengys5
 */
public class TestJoinAndSplitAnalysisMember extends JoinAndSplitAnalysisMember {

    TestJoinAndSplitAnalysisMember(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void analyse(Object message) {

    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        super.preStart();
    }

    @Override
    protected WorkerRefs aggWorkRefs() {
        return null;
    }

    public enum Role implements org.skywalking.apm.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return null;
        }

        @Override
        public WorkerSelector workerSelector() {
            return null;
        }
    }
}
