package com.a.eye.skywalking.collector.worker;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.Role;
import com.a.eye.skywalking.collector.worker.config.CacheSizeConfig;
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

    private RecordPersistenceData persistenceData;

    public RecordPersistenceMember(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
        persistenceData = new RecordPersistenceData();
    }

    private RecordPersistenceData getPersistenceData() {
        return this.persistenceData;
    }

    @Override
    public void analyse(Object message) throws Exception {
        if (message instanceof RecordData) {
            RecordData recordData = (RecordData) message;
            logger.debug("setRecord: id: %s, data: %s", recordData.getId(), recordData.getRecord());
            getPersistenceData().getElseCreate(recordData.getId()).setRecord(recordData.getRecord());
            if (getPersistenceData().size() >= CacheSizeConfig.Cache.Persistence.size) {
                persistence();
            }
        } else {
            logger.error("message unhandled");
        }
    }

    protected void persistence() {
        boolean success = saveToEs();
        if (success) {
            getPersistenceData().clear();
        }
    }

    private boolean saveToEs() {
        Client client = EsClient.INSTANCE.getClient();
        BulkRequestBuilder bulkRequest = client.prepareBulk();
        logger.debug("persistenceData size: %s", getPersistenceData().size());

        Iterator<Map.Entry<String, RecordData>> iterator = getPersistenceData().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, RecordData> recordData = iterator.next();
            logger.debug("saveToEs: key: %s, data: %s", recordData.getKey(), recordData.getValue().getRecord().toString());
            bulkRequest.add(client.prepareIndex(esIndex(), esType(), recordData.getKey()).setSource(recordData.getValue().getRecord().toString()));
        }

        BulkResponse bulkResponse = bulkRequest.execute().actionGet();
        if (bulkResponse.hasFailures()) {
            logger.error(bulkResponse.buildFailureMessage());
        }
        return !bulkResponse.hasFailures();
    }
}
