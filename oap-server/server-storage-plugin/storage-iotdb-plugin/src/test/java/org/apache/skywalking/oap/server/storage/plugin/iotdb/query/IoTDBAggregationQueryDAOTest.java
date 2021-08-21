package org.apache.skywalking.oap.server.storage.plugin.iotdb.query;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.iotdb.rpc.IoTDBConnectionException;
import org.apache.iotdb.rpc.StatementExecutionException;
import org.apache.iotdb.session.pool.SessionDataSetWrapper;
import org.apache.iotdb.session.pool.SessionPool;
import org.apache.iotdb.tsfile.read.common.Field;
import org.apache.iotdb.tsfile.read.common.RowRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.query.type.SelectedRecord;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBStorageConfig;
import org.junit.Before;
import org.junit.Test;

public class IoTDBAggregationQueryDAOTest {
    private IoTDBClient client;
    private IoTDBAggregationQueryDAO aggregationQueryDAO;

    @Before
    public void setUp() throws Exception {
        IoTDBStorageConfig config = new IoTDBStorageConfig();
        config.setHost("127.0.0.1");
        config.setRpcPort(6667);
        config.setUsername("root");
        config.setPassword("root");
        config.setStorageGroup("root.skywalking");
        config.setSessionPoolSize(3);
        config.setFetchTaskLogMaxSize(1000);

        client = new IoTDBClient(config);
        client.connect();

        aggregationQueryDAO = new IoTDBAggregationQueryDAO(client);
    }

    @Test
    public void sortMetrics() throws IOException {
        // Because building the parameter of sortMetrics is difficult, I adopt the same logic to test sortMetrics
        StringBuilder query = new StringBuilder();
        query.append(String.format("select %s from ", ServiceRelationServerSideMetrics.COMPONENT_ID))
                .append(client.getStorageGroup()).append(IoTDBClient.DOT).append(ServiceRelationServerSideMetrics.INDEX_NAME);
        query.append(" where ").append(IoTDBClient.TIME).append(" >= ").append(-30612585599000L)
                .append(" and ").append(IoTDBClient.TIME).append(" <= ").append(10000000000001L);
//        if (additionalConditions != null) {
//            additionalConditions.forEach(additionalCondition ->
//                    query.append(" and ").append(additionalCondition.getKey()).append(" = \"")
//                            .append(additionalCondition.getValue()).append("\""));
//        }
        query.append(IoTDBClient.ALIGN_BY_DEVICE);

        List<SelectedRecord> topEntities = new ArrayList<>();
        try {
            SessionPool sessionPool = client.getSessionPool();
            if (!sessionPool.checkTimeseriesExists(client.getStorageGroup() + IoTDBClient.DOT + ServiceRelationServerSideMetrics.INDEX_NAME)) {
                return;
            }
            SessionDataSetWrapper wrapper = sessionPool.executeQueryStatement(query.toString());
//            if (log.isDebugEnabled()) {
//                log.debug("SQL: {}, columnNames: {}", query, wrapper.getColumnNames());
//            }

            Map<String, Double> entityIdAndSumMap = new HashMap<>();
            Map<String, Integer> entityIdAndCountMap = new HashMap<>();
            while (wrapper.hasNext()) {
                RowRecord rowRecord = wrapper.next();
                List<Field> fields = rowRecord.getFields();
                String[] layerNames = fields.get(0).getStringValue().split("\\" + IoTDBClient.DOT + "\"");
                String entityId = client.layerName2IndexValue(layerNames[2]);
                double value = Double.parseDouble(fields.get(1).getStringValue());
                entityIdAndSumMap.merge(entityId, value, Double::sum);
                entityIdAndCountMap.merge(entityId, 1, Integer::sum);
            }

            entityIdAndSumMap.forEach((String entityId, Double sum) -> {
                double count = entityIdAndCountMap.get(entityId);
                double avg = sum / count;
                SelectedRecord topNEntity = new SelectedRecord();
                topNEntity.setId(entityId);
                topNEntity.setValue(String.valueOf(avg));
                topEntities.add(topNEntity);
            });
        } catch (IoTDBConnectionException | StatementExecutionException e) {
            throw new IOException(e);
        }
//        if (condition.getOrder().equals(Order.DES)) {
//            topEntities.sort((SelectedRecord t1, SelectedRecord t2) ->
//                    Double.compare(Double.parseDouble(t2.getValue()), Double.parseDouble(t1.getValue())));
//        } else {
//            topEntities.sort(Comparator.comparingDouble((SelectedRecord t) -> Double.parseDouble(t.getValue())));
//        }
//        int limit = condition.getTopN();
//        return limit > topEntities.size() ? topEntities : topEntities.subList(0, limit);
        topEntities.sort((SelectedRecord t1, SelectedRecord t2) ->
                Double.compare(Double.parseDouble(t2.getValue()), Double.parseDouble(t1.getValue())));
        int limit = 10;
        List<SelectedRecord> records = limit > topEntities.size() ? topEntities : topEntities.subList(0, limit);
    }
}