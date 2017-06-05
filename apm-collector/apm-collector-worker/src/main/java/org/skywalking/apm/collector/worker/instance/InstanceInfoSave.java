package org.skywalking.apm.collector.worker.instance;

import org.elasticsearch.action.index.IndexResponse;
import org.skywalking.apm.collector.actor.AbstractLocalSyncWorker;
import org.skywalking.apm.collector.actor.AbstractLocalSyncWorkerProvider;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.Role;
import org.skywalking.apm.collector.actor.selector.RollingSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.worker.instance.entity.InstanceInfo;
import org.skywalking.apm.collector.worker.storage.EsClient;

public class InstanceInfoSave extends AbstractLocalSyncWorker {
    public InstanceInfoSave(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    protected void onWork(Object request, Object response) throws Exception {
        if (request instanceof InstanceInfo) {

            InstanceInfo info = (InstanceInfo)request;
            IndexResponse indexResponse = EsClient.INSTANCE.getClient().prepareIndex(InstanceInfoIndex.INDEX,
                InstanceInfoIndex.TYPE, Long.toString(info.getInstanceId())).setSource(info.serialize()).get();

            if (indexResponse.getId() != null && indexResponse.getId().length() == 0) {
                throw new PersistenceFailedException("instance Id[" + info.getApplicationCode() + "," +
                    info.getInstanceId() + "] persistence failed");
            }
        }
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<InstanceInfoSave> {
        @Override
        public Role role() {
            return InstanceInfoSave.WorkerRole.INSTANCE;
        }

        @Override
        public InstanceInfoSave workerInstance(ClusterWorkerContext clusterContext) {
            return new InstanceInfoSave(role(), clusterContext, new LocalWorkerContext());
        }
    }

    public enum WorkerRole implements Role {
        INSTANCE;

        @Override
        public String roleName() {
            return InstanceInfoSave.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
