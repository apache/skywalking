package com.a.eye.skywalking.collector.worker;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;

/**
 * @author pengys5
 */
public class TestMergePersistenceMember extends MergePersistenceMember {
    TestMergePersistenceMember(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
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

    public enum Role implements com.a.eye.skywalking.collector.actor.Role {
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
