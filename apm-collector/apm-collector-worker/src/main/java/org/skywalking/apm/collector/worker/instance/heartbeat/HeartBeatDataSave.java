package org.skywalking.apm.collector.worker.instance.heartbeat;

import org.skywalking.apm.collector.actor.AbstractClusterWorker;
import org.skywalking.apm.collector.actor.AbstractClusterWorkerProvider;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.ProviderNotFoundException;
import org.skywalking.apm.collector.actor.selector.HashCodeSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.worker.config.WorkerConfig;
import org.skywalking.apm.collector.worker.instance.entity.HeartBeat;
import org.skywalking.apm.collector.worker.storage.RecordData;

public class HeartBeatDataSave extends AbstractClusterWorker {

    protected HeartBeatDataSave(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void preStart() throws ProviderNotFoundException {
        getClusterContext().findProvider(HeartBeatPersistenceMember.Role.INSTANCE).create(this);
    }

    @Override
    protected void onWork(Object message) throws Exception {
        if (message instanceof RecordData) {
            HeartBeat heartBeat = new HeartBeat(((RecordData)message).get());
            getSelfContext().lookup(HeartBeatPersistenceMember.Role.INSTANCE).tell(heartBeat);
        }
    }

    public static class Factory extends AbstractClusterWorkerProvider<HeartBeatDataSave> {
        @Override
        public HeartBeatDataSave.Role role() {
            return HeartBeatDataSave.Role.INSTANCE;
        }

        @Override
        public HeartBeatDataSave workerInstance(ClusterWorkerContext clusterContext) {
            return new HeartBeatDataSave(role(), clusterContext, new LocalWorkerContext());
        }

        @Override
        public int workerNum() {
            return WorkerConfig.WorkerNum.Node.HeartBeatSave.VALUE;
        }
    }

    public enum Role implements org.skywalking.apm.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return HeartBeatDataSave.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new HashCodeSelector();
        }
    }
}
