package com.a.eye.skywalking.collector.worker;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.Role;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.a.eye.skywalking.collector.worker.storage.MergeData;
import com.a.eye.skywalking.collector.worker.storage.MergePersistenceData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetRequestBuilder;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.client.Client;

import java.util.Iterator;
import java.util.Map;

/**
 * @author pengys5
 */
public abstract class MergePersistenceMember extends PersistenceMember {

    private Logger logger = LogManager.getFormatterLogger(MergePersistenceMember.class);

    private MergePersistenceData persistenceData = new MergePersistenceData();

    protected MergePersistenceMember(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    final public void analyse(Object message) throws Exception {
        if (message instanceof MergeData) {
            MergeData mergeData = (MergeData) message;
            persistenceData.getElseCreate(mergeData.getId()).merge(mergeData);
            if (persistenceData.size() >= WorkerConfig.Persistence.Data.size) {
                persistence();
            }
        } else {
            logger.error("message unhandled");
        }
    }

    final protected void persistence() {
        MultiGetResponse multiGetResponse = searchFromEs();
        for (MultiGetItemResponse itemResponse : multiGetResponse) {
            GetResponse response = itemResponse.getResponse();
            if (response != null && response.isExists()) {
                persistenceData.getElseCreate(response.getId()).merge(response.getSource());
            }
        }

        boolean success = saveToEs();
        if (success) {
            persistenceData.clear();
        }
    }

    private MultiGetResponse searchFromEs() {
        Client client = EsClient.getClient();
        MultiGetRequestBuilder multiGetRequestBuilder = client.prepareMultiGet();

        Iterator<Map.Entry<String, MergeData>> iterator = persistenceData.iterator();

        while (iterator.hasNext()) {
            multiGetRequestBuilder.add(esIndex(), esType(), iterator.next().getKey());
        }

        return multiGetRequestBuilder.get();
    }

    private boolean saveToEs() {
        Client client = EsClient.getClient();
        BulkRequestBuilder bulkRequest = client.prepareBulk();
        logger.debug("persistenceData size: %s", persistenceData.size());

        Iterator<Map.Entry<String, MergeData>> iterator = persistenceData.iterator();
        while (iterator.hasNext()) {
            MergeData mergeData = iterator.next().getValue();
            bulkRequest.add(client.prepareIndex(esIndex(), esType(), mergeData.getId()).setSource(mergeData.toMap()));
        }

        BulkResponse bulkResponse = bulkRequest.execute().actionGet();
        return !bulkResponse.hasFailures();
    }
}
