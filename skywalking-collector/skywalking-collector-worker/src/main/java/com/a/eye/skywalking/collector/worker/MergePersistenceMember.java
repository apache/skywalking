package com.a.eye.skywalking.collector.worker;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.Role;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.a.eye.skywalking.collector.worker.storage.MergeData;
import com.a.eye.skywalking.collector.worker.storage.MergePersistenceData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.client.Client;

import java.util.List;
import java.util.Map;

/**
 * @author pengys5
 */
public abstract class MergePersistenceMember extends PersistenceMember<MergePersistenceData, MergeData> {

    private Logger logger = LogManager.getFormatterLogger(MergePersistenceMember.class);

    protected MergePersistenceMember(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public MergePersistenceData initializeData() {
        return new MergePersistenceData();
    }

    @Override
    final public void analyse(Object message) throws Exception {
        if (message instanceof MergeData) {
            MergeData mergeData = (MergeData) message;
            MergePersistenceData data = getPersistenceData();
            data.hold();
            data.getOrCreate(mergeData.getId()).merge(mergeData);
            data.release();
        } else {
            logger.error("unhandled message, message instance must MergeData, but is %s", message.getClass().toString());
        }
    }

    @Override
    final protected void prepareIndex(List<IndexRequestBuilder> builderList) {
        Map<String, MergeData> lastData = getPersistenceData().getLast().asMap();
        extractData(lastData);

        Client client = EsClient.INSTANCE.getClient();
        lastData.forEach((key, value) -> {
            IndexRequestBuilder builder = client.prepareIndex(esIndex(), esType(), key).setSource(value.asMap());
            builderList.add(builder);
        });
        lastData.clear();
    }
}
