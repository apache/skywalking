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
import org.skywalking.apm.collector.worker.storage.MetricData;
import org.skywalking.apm.collector.worker.storage.MetricPersistenceData;

/**
 * @author pengys5
 */
public abstract class MetricPersistenceMember extends PersistenceMember<MetricPersistenceData, MetricData> {

    private Logger logger = LogManager.getFormatterLogger(MetricPersistenceMember.class);

    public MetricPersistenceMember(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    @Override
    public MetricPersistenceData initializeData() {
        return new MetricPersistenceData();
    }

    @Override final public void analyse(Object message) {
        if (message instanceof MetricData) {
            MetricData metricData = (MetricData)message;
            MetricPersistenceData data = getPersistenceData();
            data.hold();
            data.getOrCreate(metricData.getId()).merge(metricData);
            data.release();
        } else {
            logger.error("unhandled message, message instance must MetricData, but is %s", message.getClass().toString());
        }
    }

    @Override final protected void prepareIndex(List<IndexRequestBuilder> indexRequestBuilders,
        List<UpdateRequestBuilder> updateRequestBuilderList) {
        Map<String, MetricData> lastData = getPersistenceData().getLast().asMap();
        extractData(lastData);

        Client client = EsClient.INSTANCE.getClient();
        lastData.forEach((key, value) -> {
            IndexRequestBuilder builder = client.prepareIndex(esIndex(), esType(), key).setSource(value.asMap());
            indexRequestBuilders.add(builder);
        });
        lastData.clear();
    }
}
