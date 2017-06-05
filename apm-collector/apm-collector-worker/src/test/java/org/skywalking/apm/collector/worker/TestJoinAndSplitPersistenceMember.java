package org.skywalking.apm.collector.worker;

import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;

/**
 * @author pengys5
 */
public class TestJoinAndSplitPersistenceMember extends JoinAndSplitPersistenceMember {
    TestJoinAndSplitPersistenceMember(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public String esIndex() {
        return null;
    }

    @Override
    public String esType() {
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
