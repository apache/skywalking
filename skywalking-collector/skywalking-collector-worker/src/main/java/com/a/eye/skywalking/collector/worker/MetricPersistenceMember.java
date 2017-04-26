package com.a.eye.skywalking.collector.worker;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.Role;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.a.eye.skywalking.collector.worker.storage.MetricData;
import com.a.eye.skywalking.collector.worker.storage.MetricPersistenceData;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.client.Client;

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

    @Override
    final public void analyse(Object message) throws Exception {
        if (message instanceof MetricData) {
            MetricData metricData = (MetricData) message;
            MetricPersistenceData data = getPersistenceData();
            data.hold();
            data.getOrCreate(metricData.getId()).merge(metricData);
            data.release();
        } else {
            logger.error("unhandled message, message instance must MetricData, but is %s", message.getClass().toString());
        }
    }

    @Override
    final protected void prepareIndex(List<IndexRequestBuilder> builderList) {
        Map<String, MetricData> lastData = getPersistenceData().getLast().asMap();
        extractData(lastData);

        Client client = EsClient.INSTANCE.getClient();
        lastData.forEach((key, value) -> {
            IndexRequestBuilder builder = client.prepareIndex(esIndex(), esType(), key).setSource(value.asMap());
            builderList.add(builder);
        });
        lastData.clear();
    }
}