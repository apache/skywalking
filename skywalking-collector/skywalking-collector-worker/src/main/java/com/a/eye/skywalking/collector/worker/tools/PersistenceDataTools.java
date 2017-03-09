package com.a.eye.skywalking.collector.worker.tools;

import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.a.eye.skywalking.collector.worker.storage.MetricPersistenceData;
import com.a.eye.skywalking.collector.worker.storage.RecordPersistenceData;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetRequestBuilder;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.client.Client;

import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class PersistenceDataTools {

    private static Logger logger = LogManager.getFormatterLogger(PersistenceDataTools.class);

    private static final String s10 = "s10";
    private static final String s20 = "s20";
    private static final String s30 = "s30";
    private static final String s40 = "s40";
    private static final String s50 = "s50";
    private static final String s60 = "s60";

    public static Map<String, Long> getFilledPersistenceData() {
        Map<String, Long> columns = new HashMap();
        columns.put(s10, 0L);
        columns.put(s20, 0L);
        columns.put(s30, 0L);
        columns.put(s40, 0L);
        columns.put(s50, 0L);
        columns.put(s60, 0L);
        return columns;
    }

    public static String second2ColumnName(int second) {
        if (second <= 10) {
            return s10;
        } else if (second > 10 && second <= 20) {
            return s20;
        } else if (second > 20 && second <= 30) {
            return s30;
        } else if (second > 30 && second <= 40) {
            return s40;
        } else if (second > 40 && second <= 50) {
            return s50;
        } else {
            return s60;
        }
    }

    public static boolean saveToEs(String esIndex, String esType, MetricPersistenceData persistenceData) {
        Client client = EsClient.getClient();
        BulkRequestBuilder bulkRequest = client.prepareBulk();
        logger.debug("persistenceData size: %s", persistenceData.size());

        for (Map.Entry<String, Map<String, Long>> entry : persistenceData.getData().entrySet()) {
            bulkRequest.add(client.prepareIndex(esIndex, esType, entry.getKey()).setSource(entry.getValue()));
        }

        BulkResponse bulkResponse = bulkRequest.execute().actionGet();
        return !bulkResponse.hasFailures();
    }

    public static boolean saveToEs(String esIndex, String esType, RecordPersistenceData persistenceData) {
        Client client = EsClient.getClient();
        BulkRequestBuilder bulkRequest = client.prepareBulk();
        logger.debug("persistenceData size: %s", persistenceData.size());

        for (Map.Entry<String, JsonObject> entry : persistenceData.getData().entrySet()) {
            logger.debug("record: %s", entry.getValue().toString());
            bulkRequest.add(client.prepareIndex(esIndex, esType, entry.getKey()).setSource(entry.getValue().toString()));
        }

        BulkResponse bulkResponse = bulkRequest.execute().actionGet();
        return !bulkResponse.hasFailures();
    }

    public static Map<String, Map<String, Object>> searchEs(String esIndex, String esType, MetricPersistenceData persistenceData) {
        Client client = EsClient.getClient();
        Map<String, Map<String, Object>> dataInEs = new HashMap();

        MultiGetRequestBuilder multiGetRequestBuilder = client.prepareMultiGet();
        for (Map.Entry<String, Map<String, Long>> entry : persistenceData.getData().entrySet()) {
            multiGetRequestBuilder.add(esIndex, esType, entry.getKey());
        }

        MultiGetResponse multiGetResponse = multiGetRequestBuilder.get();
        for (MultiGetItemResponse itemResponse : multiGetResponse) {
            GetResponse response = itemResponse.getResponse();
            if (response != null && response.isExists()) {
                dataInEs.put(response.getId(), response.getSource());
            }
        }
        return dataInEs;
    }

    public static MetricPersistenceData dbData2PersistenceData(Map<String, Map<String, Object>> dbData) {
        MetricPersistenceData persistenceData = new MetricPersistenceData();

        for (Map.Entry<String, Map<String, Object>> entryLines : dbData.entrySet()) {
            for (Map.Entry<String, Object> entryColumns : entryLines.getValue().entrySet()) {
                persistenceData.setMetric(entryLines.getKey(), entryColumns.getKey(), Long.valueOf(entryColumns.getValue().toString()));
            }
        }

        return persistenceData;
    }

    public static void mergeData(MetricPersistenceData dbData, MetricPersistenceData memoryData) {
        for (Map.Entry<String, Map<String, Long>> memoryEntry : memoryData.getData().entrySet()) {
            String id = memoryEntry.getKey();
            if (dbData.getData().containsKey(id)) {
                for (Map.Entry<String, Long> memoryMetricEntry : memoryEntry.getValue().entrySet()) {
                    memoryMetricEntry.setValue(dbData.getData().get(id).get(memoryMetricEntry.getKey()) + memoryMetricEntry.getValue());
                }
            }
        }
    }
}
