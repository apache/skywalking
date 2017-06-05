package org.skywalking.apm.collector.worker.instance.heartbeat;

import java.util.List;
import java.util.Map;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.client.Client;
import org.skywalking.apm.collector.actor.AbstractLocalSyncWorkerProvider;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.selector.HashCodeSelector;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;
import org.skywalking.apm.collector.worker.PersistenceMember;
import org.skywalking.apm.collector.worker.instance.InstanceInfoIndex;
import org.skywalking.apm.collector.worker.instance.entity.HeartBeat;
import org.skywalking.apm.collector.worker.storage.EsClient;
import org.skywalking.apm.collector.worker.storage.PersistenceWorkerListener;

public class HeartBeatPersistenceMember extends PersistenceMember<HeartBeatPersistenceData, HeartBeat> {
    public HeartBeatPersistenceMember(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public HeartBeatPersistenceData initializeData() {
        return new HeartBeatPersistenceData();
    }

    @Override
    public String esIndex() {
        return InstanceInfoIndex.INDEX;
    }

    @Override
    public String esType() {
        return InstanceInfoIndex.TYPE;
    }

    @Override
    public void analyse(Object message) throws Exception {
        if (message instanceof HeartBeat) {
            HeartBeat heartBeat = (HeartBeat)message;
            getPersistenceData().hold();
            getPersistenceData().getOrCreate(heartBeat.getId()).merge(heartBeat);
            getPersistenceData().release();
        }
    }

    @Override
    protected void prepareIndex(List<IndexRequestBuilder> builderList) {
        Map<String, HeartBeat> lastData = getPersistenceData().getLast().asMap();

        Client client = EsClient.INSTANCE.getClient();
        lastData.forEach((key, value) -> {
            IndexRequestBuilder builder = client.prepareIndex(esIndex(), esType(), key).setSource(value.asMap());
            builderList.add(builder);
        });
        lastData.clear();
    }

    public static class Factory extends AbstractLocalSyncWorkerProvider<HeartBeatPersistenceMember> {
        @Override
        public HeartBeatPersistenceMember.Role role() {
            return HeartBeatPersistenceMember.Role.INSTANCE;
        }

        @Override
        public HeartBeatPersistenceMember workerInstance(ClusterWorkerContext clusterContext) {
            HeartBeatPersistenceMember worker = new HeartBeatPersistenceMember(role(), clusterContext, new LocalWorkerContext());
            PersistenceWorkerListener.INSTANCE.register(worker);
            return worker;
        }
    }

    public enum Role implements org.skywalking.apm.collector.actor.Role {
        INSTANCE;

        @Override
        public String roleName() {
            return HeartBeatPersistenceMember.class.getSimpleName();
        }

        @Override
        public WorkerSelector workerSelector() {
            return new HashCodeSelector();
        }
    }
}
