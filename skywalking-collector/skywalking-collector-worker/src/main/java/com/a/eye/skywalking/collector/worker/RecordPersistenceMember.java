package com.a.eye.skywalking.collector.worker;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.Role;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.a.eye.skywalking.collector.worker.storage.RecordData;
import com.a.eye.skywalking.collector.worker.storage.RecordPersistenceData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.Client;

import java.util.Iterator;
import java.util.Map;

/**
 * @author pengys5
 */
public abstract class RecordPersistenceMember extends PersistenceMember {

    private Logger logger = LogManager.getFormatterLogger(RecordPersistenceMember.class);

    protected RecordPersistenceData persistenceData = new RecordPersistenceData();

    public RecordPersistenceMember(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public void analyse(Object message) throws Exception {
        if (message instanceof RecordData) {
            RecordData recordData = (RecordData) message;
            persistenceData.getElseCreate(recordData.getId()).setRecord(recordData.getRecord());
            if (persistenceData.size() >= WorkerConfig.Persistence.Data.size) {
                persistence();
            }
        } else {
            logger.error("message unhandled");
        }
    }

    protected void persistence() {
        boolean success = saveToEs();
        if (success) {
            persistenceData.clear();
        }
    }

    public boolean saveToEs() {
        Client client = EsClient.getClient();
        BulkRequestBuilder bulkRequest = client.prepareBulk();
        logger.debug("persistenceData size: %s", persistenceData.size());

        Iterator<Map.Entry<String, RecordData>> iterator = persistenceData.iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, RecordData> recordData = iterator.next();
            bulkRequest.add(client.prepareIndex(esIndex(), esType(), recordData.getKey()).setSource(recordData.getValue().getRecord().toString()));
        }

        BulkResponse bulkResponse = bulkRequest.execute().actionGet();
        return !bulkResponse.hasFailures();
    }
}
