package org.skywalking.apm.collector.worker.instance.persistence;

import com.google.gson.Gson;
import org.skywalking.apm.collector.actor.AbstractLocalSyncWorkerProvider;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.selector.RollingSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.worker.RecordPersistenceMember;
import org.skywalking.apm.collector.worker.instance.InstanceIndex;
import org.skywalking.apm.collector.worker.storage.EsClient;
import org.skywalking.apm.collector.worker.storage.PersistenceWorkerListener;
import org.skywalking.apm.collector.worker.storage.RecordData;

public class InstanceSave extends RecordPersistenceMember {

    public InstanceSave(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public String esIndex() {
        return InstanceIndex.INDEX;
    }

    @Override
    public String esType() {
        return InstanceIndex.TYPE_RECORD;
    }

    @Override
    public void analyse(Object message) {
        if (message instanceof RecordData) {
            persistence((RecordData)message);
        }
    }

    private void persistence(RecordData message) {
        String recordDataStr = new Gson().toJson(message.get());
        EsClient.INSTANCE.getClient().prepareIndex(esIndex(), esType(), message.getId()).setSource(recordDataStr).get();
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<InstanceSave> {
        @Override
        public InstanceSave.Role role() {
            return InstanceSave.Role.INSTANCE;
        }

        @Override
        public InstanceSave workerInstance(ClusterWorkerContext clusterContext) {
            InstanceSave worker = new InstanceSave(role(), clusterContext, new LocalWorkerContext());
            PersistenceWorkerListener.INSTANCE.register(worker);
            return worker;
        }
    }

    public enum Role implements org.skywalking.apm.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return InstanceSave.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new RollingSelector();
        }
    }
}
