package com.a.eye.skywalking.collector.worker;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.Role;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.a.eye.skywalking.collector.worker.storage.RecordData;
import com.a.eye.skywalking.collector.worker.storage.RecordPersistenceData;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.client.Client;

/**
 * @author pengys5
 */
public abstract class RecordPersistenceMember extends PersistenceMember<RecordPersistenceData, RecordData> {

    private Logger logger = LogManager.getFormatterLogger(RecordPersistenceMember.class);

    public RecordPersistenceMember(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    final public RecordPersistenceData initializeData() {
        return new RecordPersistenceData();
    }

    @Override
    public void analyse(Object message) throws Exception {
        if (message instanceof RecordData) {
            RecordData recordData = (RecordData) message;
            logger.debug("setRecord: id: %s, data: %s", recordData.getId(), recordData.getRecord());
            RecordPersistenceData data = getPersistenceData();
            data.holdData();
            data.getElseCreate(recordData.getId()).setRecord(recordData.getRecord());
            data.releaseData();
        } else {
            logger.error("message unhandled");
        }
    }

    @Override
    final protected void prepareIndex(List<IndexRequestBuilder> builderList) {
        Map<String, RecordData> lastData = getPersistenceData().getLast().asMap();
        extractData(lastData);

        Client client = EsClient.INSTANCE.getClient();
        lastData.forEach((key, value) -> {
            IndexRequestBuilder builder = client.prepareIndex(esIndex(), esType(), key).setSource(value.getRecord().toString());
            builderList.add(builder);
        });
        lastData.clear();
    }
}
