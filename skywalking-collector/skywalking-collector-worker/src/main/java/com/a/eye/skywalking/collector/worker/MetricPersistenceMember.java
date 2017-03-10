package com.a.eye.skywalking.collector.worker;

import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.queue.MessageHolder;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.a.eye.skywalking.collector.worker.storage.MetricData;
import com.a.eye.skywalking.collector.worker.storage.MetricPersistenceData;
import com.lmax.disruptor.RingBuffer;
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
public abstract class MetricPersistenceMember extends PersistenceMember {

    private Logger logger = LogManager.getFormatterLogger(MetricPersistenceMember.class);

    protected MetricPersistenceData persistenceData = new MetricPersistenceData();

    public MetricPersistenceMember(RingBuffer<MessageHolder> ringBuffer, ActorRef actorRef) {
        super(ringBuffer, actorRef);
    }

    @Override
    public void analyse(Object message) throws Exception {
        if (message instanceof MetricData) {
            MetricData metricData = (MetricData) message;
            persistenceData.getElseCreate(metricData.getId()).merge(metricData);
            if (persistenceData.size() >= WorkerConfig.Persistence.Data.size) {
                persistence();
            }
        } else {
            logger.error("message unhandled");
        }
    }

    protected void persistence() {
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

    public MultiGetResponse searchFromEs() {
        Client client = EsClient.getClient();
        MultiGetRequestBuilder multiGetRequestBuilder = client.prepareMultiGet();

        Iterator<Map.Entry<String, MetricData>> iterator = persistenceData.iterator();
        while (iterator.hasNext()) {
            multiGetRequestBuilder.add(esIndex(), esType(), iterator.next().getKey());
        }

        MultiGetResponse multiGetResponse = multiGetRequestBuilder.get();
        return multiGetResponse;
    }

    public boolean saveToEs() {
        Client client = EsClient.getClient();
        BulkRequestBuilder bulkRequest = client.prepareBulk();
        logger.debug("persistenceData size: %s", persistenceData.size());

        Iterator<Map.Entry<String, MetricData>> iterator = persistenceData.iterator();
        while (iterator.hasNext()) {
            MetricData metricData = iterator.next().getValue();
            bulkRequest.add(client.prepareIndex(esIndex(), esType(), metricData.getId()).setSource(metricData.toMap()));
        }

        BulkResponse bulkResponse = bulkRequest.execute().actionGet();
        return !bulkResponse.hasFailures();
    }
}
