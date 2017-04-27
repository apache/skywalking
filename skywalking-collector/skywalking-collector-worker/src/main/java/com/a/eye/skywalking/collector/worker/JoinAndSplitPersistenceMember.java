package com.a.eye.skywalking.collector.worker;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.Role;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.a.eye.skywalking.collector.worker.storage.JoinAndSplitData;
import com.a.eye.skywalking.collector.worker.storage.JoinAndSplitPersistenceData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.client.Client;

import java.util.List;
import java.util.Map;

/**
 * @author pengys5
 */
public abstract class JoinAndSplitPersistenceMember extends PersistenceMember<JoinAndSplitPersistenceData, JoinAndSplitData> {

    private Logger logger = LogManager.getFormatterLogger(JoinAndSplitPersistenceMember.class);

    protected JoinAndSplitPersistenceMember(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public JoinAndSplitPersistenceData initializeData() {
        return new JoinAndSplitPersistenceData();
    }

    @Override
    final public void analyse(Object message) throws Exception {
        if (message instanceof JoinAndSplitData) {
            JoinAndSplitData joinAndSplitData = (JoinAndSplitData) message;
            JoinAndSplitPersistenceData data = getPersistenceData();
            data.hold();
            data.getOrCreate(joinAndSplitData.getId()).merge(joinAndSplitData);
            data.release();
        } else {
            logger.error("unhandled message, message instance must JoinAndSplitData, but is %s", message.getClass().toString());
        }
    }

    @Override
    final protected void prepareIndex(List<IndexRequestBuilder> builderList) {
        Map<String, JoinAndSplitData> lastData = getPersistenceData().getLast().asMap();
        extractData(lastData);

        Client client = EsClient.INSTANCE.getClient();
        lastData.forEach((key, value) -> {
            IndexRequestBuilder builder = client.prepareIndex(esIndex(), esType(), key).setSource(value.asMap());
            builderList.add(builder);
        });
        lastData.clear();
    }
}
