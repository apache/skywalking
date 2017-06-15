package org.skywalking.apm.collector.worker;

import java.util.List;
import java.util.Map;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.client.Client;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.Role;
import org.skywalking.apm.collector.worker.storage.EsClient;
import org.skywalking.apm.collector.worker.storage.RecordData;
import org.skywalking.apm.collector.worker.storage.RecordPersistenceData;

/**
 * @author pengys5
 */
public abstract class RecordPersistenceMember extends PersistenceMember<RecordPersistenceData, RecordData> {

    public RecordPersistenceMember(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override final public RecordPersistenceData initializeData() {
        return new RecordPersistenceData();
    }

    @Override
    public void analyse(Object message) {
        if (message instanceof RecordData) {
            RecordData recordData = (RecordData)message;
            logger().debug("set: id: %s, data: %s", recordData.getId(), recordData.get());
            RecordPersistenceData data = getPersistenceData();
            data.hold();
            data.getOrCreate(recordData.getId()).set(recordData.get());
            data.release();
        } else {
            logger().error("message unhandled");
        }
    }

    @Override final protected void prepareIndex(List<IndexRequestBuilder> indexRequestBuilders,
        List<UpdateRequestBuilder> updateRequestBuilderList) {
        Map<String, RecordData> lastData = getPersistenceData().getLast().asMap();
        extractData(lastData);

        Client client = EsClient.INSTANCE.getClient();
        lastData.forEach((key, value) -> {
            IndexRequestBuilder builder = client.prepareIndex(esIndex(), esType(), key).setSource(value.get().toString());
            indexRequestBuilders.add(builder);
        });
        lastData.clear();
    }
}
