package org.skywalking.apm.collector.worker;

import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.client.Client;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.Role;
import org.skywalking.apm.collector.worker.storage.EsClient;
import org.skywalking.apm.collector.worker.storage.JoinAndSplitData;
import org.skywalking.apm.collector.worker.storage.JoinAndSplitPersistenceData;

/**
 * @author pengys5
 */
public abstract class JoinAndSplitPersistenceMember extends PersistenceMember<JoinAndSplitPersistenceData, JoinAndSplitData> {

    private Logger logger = LogManager.getFormatterLogger(JoinAndSplitPersistenceMember.class);

    protected JoinAndSplitPersistenceMember(Role role, ClusterWorkerContext clusterContext,
        LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public JoinAndSplitPersistenceData initializeData() {
        return new JoinAndSplitPersistenceData();
    }

    @Override final public void analyse(Object message) {
        if (message instanceof JoinAndSplitData) {
            JoinAndSplitData joinAndSplitData = (JoinAndSplitData)message;
            JoinAndSplitPersistenceData data = getPersistenceData();
            data.hold();
            data.getOrCreate(joinAndSplitData.getId()).merge(joinAndSplitData);
            data.release();
        } else {
            logger.error("unhandled message, message instance must JoinAndSplitData, but is %s", message.getClass().toString());
        }
    }

    @Override final protected void prepareIndex(List<IndexRequestBuilder> indexRequestBuilders,
        List<UpdateRequestBuilder> updateRequestBuilderList) {
        Map<String, JoinAndSplitData> lastData = getPersistenceData().getLast().asMap();
        extractData(lastData);

        Client client = EsClient.INSTANCE.getClient();
        lastData.forEach((key, value) -> {
            IndexRequestBuilder builder = client.prepareIndex(esIndex(), esType(), key).setSource(value.asMap());
            indexRequestBuilders.add(builder);
        });
        lastData.clear();
    }
}
