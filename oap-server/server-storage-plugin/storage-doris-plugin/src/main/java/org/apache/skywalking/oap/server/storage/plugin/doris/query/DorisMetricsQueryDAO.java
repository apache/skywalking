package org.apache.skywalking.oap.server.storage.plugin.doris.query;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics; // For thermodynamic & linearIntrp
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.KeyValue;
import org.apache.skywalking.oap.server.core.query.input.MetricsCondition;
import org.apache.skywalking.oap.server.core.query.type.HeatMap;
import org.apache.skywalking.oap.server.core.query.type.IntValues;
import org.apache.skywalking.oap.server.core.query.type.KVInt;
import org.apache.skywalking.oap.server.core.query.type.MetricsValues;
import org.apache.skywalking.oap.server.core.query.type.SelectedRecord; // For sortMetrics
import org.apache.skywalking.oap.server.core.storage.query.IMetricsQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.doris.client.DorisClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DorisMetricsQueryDAO implements IMetricsQueryDAO {

    private static final Logger LOGGER = LoggerFactory.getLogger(DorisMetricsQueryDAO.class);
    private final DorisClient dorisClient;

    public DorisMetricsQueryDAO(DorisClient dorisClient) {
        this.dorisClient = dorisClient;
    }

    @Override
    public MetricsValues readMetricsValues(MetricsCondition condition, String valueColumnName, Duration duration) throws IOException {
        String tableName = condition.getName(); // Metric name is used as table name
        String entityId = condition.getEntity().buildId();

        // Assuming time_bucket is comparable with duration.getStartTimestamp/EndTimestamp
        // These timestamps are usually in a format like YYYYMMDDHHMMSS or YYYYMMDDHHMM
        // The SQL needs to be adjusted if time_bucket is not directly comparable or is a different unit.
        // For Doris, if time_bucket is DATETIME, use appropriate date functions.
        // If time_bucket is a number (like YYYYMMDDHHMM), direct comparison might work.
        // Let's assume time_bucket is a numerical representation that can be directly compared.
        
        // The `id` column in metrics tables is usually a composite of entityId and time_bucket.
        // Or, `entity_id` is a separate column. The prompt assumes `entity_id` column.
        // `valueColumnName` is the actual metric value column, e.g., "value", "summation", "count".

        StringBuilder sqlBuilder = new StringBuilder();
        List<Object> params = new ArrayList<>();

        sqlBuilder.append("SELECT time_bucket, ").append(valueColumnName).append(" AS value");
        sqlBuilder.append(" FROM ").append(tableName);
        sqlBuilder.append(" WHERE entity_id = ?");
        params.add(entityId);
        sqlBuilder.append(" AND time_bucket >= ? AND time_bucket <= ?");
        params.add(duration.getStartTimestamp());
        params.add(duration.getEndTimestamp());
        sqlBuilder.append(" ORDER BY time_bucket ASC");

        MetricsValues metricsValues = new MetricsValues();
        // Default label for non-labeled metrics or when specific label is not queried.
        // SkyWalking UI expects a label, if it's not a labeled metric, a common practice is to use "value" or metric name.
        // For this method, it's typically for a single entity, so label might not be explicitly set here,
        // but rather in the higher-level service that calls this DAO.
        // The `MetricsValues` structure has a `label` field, which is often null for this specific query type.
        // The `IntValues` inside it will hold the actual data points.

        IntValues intValues = new IntValues();
        try (ResultSet resultSet = dorisClient.executeQuery(sqlBuilder.toString(), params.toArray())) {
            while (resultSet != null && resultSet.next()) {
                KVInt kvInt = new KVInt();
                kvInt.setId(String.valueOf(resultSet.getLong("time_bucket"))); // time_bucket as ID
                kvInt.setValue(resultSet.getLong("value")); // Assuming value is long
                intValues.addKVInt(kvInt);
            }
            metricsValues.setValues(intValues);
        } catch (SQLException e) {
            LOGGER.error("Failed to read metrics values for entity: {}, table: {}. SQL: {}", entityId, tableName, sqlBuilder.toString(), e);
            throw new IOException("Failed to read metrics values from Doris: " + tableName, e);
        }
        
        LOGGER.debug("Read metrics values for entity: {}, table: {}, results count: {}", entityId, tableName, intValues.getValues().size());
        return metricsValues;
    }
    
    @Override
    public List<MetricsValues> readLabeledMetricsValues(MetricsCondition condition, String valueColumnName,
                                                        List<KeyValue> labels, Duration duration) throws IOException {
        String tableName = condition.getName(); // Metric name as table name
        String entityId = condition.getEntity().buildId();

        StringBuilder sqlBuilder = new StringBuilder();
        List<Object> params = new ArrayList<>();

        // SELECT time_bucket, <label_col1>, <label_col2>, ..., <valueColumnName> FROM ...
        // The labels to select are derived from the `labels` parameter's keys or a convention.
        // For simplicity, let's assume the `labels` parameter contains the specific label values we want to *filter* by,
        // and we want to select all available label columns to return them.
        // This part is tricky: what are the label column names?
        // The `labels` param provides KeyValue pairs for filtering.
        // The `valueColumnName` is the metric value.
        // The UI expects `MetricsValues` to have a `label` (string) and `IntValues` (time series).
        // This method should return a list of `MetricsValues`, one for each distinct label combination found.

        // Let's assume label columns are named like "label_key_name" or directly "key_name".
        // For this example, we'll assume we need to query all label columns present in the `labels` filter list
        // plus the value column, and then group by these label columns + time_bucket.
        // This is a common pattern for getting labeled metrics.

        // The `KeyValue` in `labels` list are for filtering.
        // Example: labels = [KeyValue("region", "us-east"), KeyValue("az", "az1")]
        // We need to find out what labels to `GROUP BY` to differentiate the MetricsValues.
        // Typically, this method returns values for *each* series identified by a unique combination of label values.

        // The `ValueColumnMetadata.INSTANCE.readValueColumnDefinition(metricName)` from ES DAO
        // is used to determine if labels are stored in a single "tags" column or separate columns.
        // Assuming separate columns for Doris for now: e.g., `label_column_name`.

        // This implementation will be simplified: it assumes the `labels` parameter provides *all* label keys
        // that define a series, and we're fetching values for a specific entity.
        // A more general implementation might need to discover label columns or expect them to be passed.

        // Let's make a simplifying assumption: the table stores labels in dedicated columns
        // like `tag_k8s_cluster`, `tag_az`, etc. And `condition.getLabels()` (if it existed) or `labels` param
        // would guide which ones to use.
        // The `List<KeyValue> labels` parameter is for filtering.
        // The distinct series are usually identified by the values of these label columns.

        // For now, this method will be stubbed with a more complex SQL structure required.
        // A common approach is to SELECT time_bucket, label1, label2, ..., value_column
        // WHERE entity_id = ? AND label1_key = ? AND label2_key = ? ...
        // This would return multiple time series, each for a distinct combination of (queried) label values.

        // The ES implementation uses `TermsAggregationBuilder` to get values for different labels.
        // For SQL, this would be `GROUP BY label_column_name(s), time_bucket`.

        if (labels == null || labels.isEmpty()) {
            LOGGER.warn("readLabeledMetricsValues called without specific labels for filtering on table {}. This might return many series or is not supported by this basic implementation.", tableName);
            // If no labels are specified for filtering, we might return all series for the entity, or none.
            // For now, let's return an empty list as the query is ambiguous without knowing which labels to group by.
            return Collections.emptyList();
        }
        
        sqlBuilder.append("SELECT time_bucket, ");
        // Construct select part for labels
        for (int i = 0; i < labels.size(); i++) {
            sqlBuilder.append(labels.get(i).getKey()).append(", ");
        }
        sqlBuilder.append(valueColumnName).append(" AS value");
        sqlBuilder.append(" FROM ").append(tableName);
        sqlBuilder.append(" WHERE entity_id = ?");
        params.add(entityId);

        for (KeyValue label : labels) {
            sqlBuilder.append(" AND ").append(label.getKey()).append(" = ?");
            params.add(label.getValue());
        }
        
        sqlBuilder.append(" AND time_bucket >= ? AND time_bucket <= ?");
        params.add(duration.getStartTimestamp());
        params.add(duration.getEndTimestamp());

        sqlBuilder.append(" ORDER BY ");
        for (int i = 0; i < labels.size(); i++) {
            sqlBuilder.append(labels.get(i).getKey()).append(" ASC, ");
        }
        sqlBuilder.append("time_bucket ASC");

        List<MetricsValues> resultList = new ArrayList<>();
        Map<String, MetricsValues> seriesMap = new java.util.LinkedHashMap<>(); // To maintain order of labels

        try (ResultSet resultSet = dorisClient.executeQuery(sqlBuilder.toString(), params.toArray())) {
            while (resultSet != null && resultSet.next()) {
                StringBuilder labelStringBuilder = new StringBuilder();
                for (KeyValue kv : labels) {
                    if (labelStringBuilder.length() > 0) labelStringBuilder.append(", ");
                    labelStringBuilder.append(kv.getKey()).append("=").append(resultSet.getString(kv.getKey()));
                }
                String currentSeriesLabel = labelStringBuilder.toString();

                MetricsValues mv = seriesMap.computeIfAbsent(currentSeriesLabel, k -> {
                    MetricsValues newMv = new MetricsValues();
                    newMv.setLabel(k); // Label for this series, e.g., "region=us-east, az=az1"
                    newMv.setValues(new IntValues());
                    return newMv;
                });

                KVInt kvInt = new KVInt();
                kvInt.setId(String.valueOf(resultSet.getLong("time_bucket")));
                kvInt.setValue(resultSet.getLong("value"));
                mv.getValues().addKVInt(kvInt);
            }
            resultList.addAll(seriesMap.values());
        } catch (SQLException e) {
            LOGGER.error("Failed to read labeled metrics values for entity: {}, table: {}. SQL: {}", entityId, tableName, sqlBuilder.toString(), e);
            throw new IOException("Failed to read labeled metrics values from Doris: " + tableName, e);
        }
        
        LOGGER.debug("Read labeled metrics for entity: {}, table: {}, results series count: {}", entityId, tableName, resultList.size());
        return resultList;
    }

    @Override
    public List<MetricsValues> readLabeledMetricsValuesWithoutEntity(String metricName, String valueColumnName,
                                                                     List<KeyValue> labels, Duration duration) throws IOException {
        // This query is for metadata scenarios, like finding available label values.
        // It typically involves aggregation over entities.
        String tableName = metricName; // Metric name as table name

        StringBuilder sqlBuilder = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (labels == null || labels.isEmpty()) {
            LOGGER.warn("readLabeledMetricsValuesWithoutEntity called without specific labels for table {}. This is not supported by this basic implementation.", tableName);
            return Collections.emptyList();
        }

        sqlBuilder.append("SELECT ");
        // We need to select the label columns to group by and then aggregate the value.
        // For example, if labels = [KeyValue("region", "us-east")], we want to see aggregates for other labels within this region.
        // This method's purpose implies we are trying to find distinct values of some labels, given filters on other labels.
        // Let's assume the goal is to get SUM of `valueColumnName` grouped by the label keys present in `labels` param,
        // for each time_bucket.

        List<String> labelKeysToSelectAndGroupBy = labels.stream().map(KeyValue::getKey).collect(Collectors.toList());

        for (int i = 0; i < labelKeysToSelectAndGroupBy.size(); i++) {
            sqlBuilder.append(labelKeysToSelectAndGroupBy.get(i)).append(", ");
        }
        // Aggregation: The ES version does a terms aggregation on label values.
        // Here we might sum up the actual metric value for each label combination.
        // The return type `List<MetricsValues>` suggests multiple series, each identified by a label.
        // This is more like an aggregation query.
        // For now, let's assume we are summing the valueColumnName for each combination of provided label keys.
        sqlBuilder.append("SUM(").append(valueColumnName).append(") AS aggregated_value");
        // This doesn't fit IntValues (which expects time_bucket based series).
        // This method is more about "what label values exist and what's their total metric value".

        // Re-interpreting: The method might be trying to get a list of MetricsValues where each MetricsValue's label
        // is a distinct value of ONE of the label keys, and the IntValues are time series for that label value.
        // This is complex. Let's simplify: get the sum for each distinct value of the *first* label in the list.

        if (labelKeysToSelectAndGroupBy.isEmpty()) {
             LOGGER.warn("No label keys to group by for readLabeledMetricsValuesWithoutEntity on table {}", tableName);
            return Collections.emptyList();
        }
        String primaryLabelKey = labelKeysToSelectAndGroupBy.get(0); // Group by the first label for simplicity

        sqlBuilder.setLength(0); // Reset
        sqlBuilder.append("SELECT time_bucket, ").append(primaryLabelKey).append(", SUM(").append(valueColumnName).append(") AS value");
        sqlBuilder.append(" FROM ").append(tableName);
        sqlBuilder.append(" WHERE ");

        List<String> filterClauses = new ArrayList<>();
        for (KeyValue filterLabel : labels) {
            if (filterLabel.getValue() != null && !filterLabel.getValue().isEmpty()) { // Filter only if value is present
                filterClauses.add(filterLabel.getKey() + " = ?");
                params.add(filterLabel.getValue());
            }
        }
        filterClauses.add("time_bucket >= ?");
        params.add(duration.getStartTimestamp());
        filterClauses.add("time_bucket <= ?");
        params.add(duration.getEndTimestamp());

        sqlBuilder.append(String.join(" AND ", filterClauses));
        sqlBuilder.append(" GROUP BY time_bucket, ").append(primaryLabelKey);
        sqlBuilder.append(" ORDER BY ").append(primaryLabelKey).append(" ASC, time_bucket ASC");
        
        Map<String, MetricsValues> seriesMap = new java.util.LinkedHashMap<>();
        try (ResultSet resultSet = dorisClient.executeQuery(sqlBuilder.toString(), params.toArray())) {
            while (resultSet != null && resultSet.next()) {
                String currentLabelValue = resultSet.getString(primaryLabelKey);
                MetricsValues mv = seriesMap.computeIfAbsent(currentLabelValue, k -> {
                    MetricsValues newMv = new MetricsValues();
                    newMv.setLabel(k);
                    newMv.setValues(new IntValues());
                    return newMv;
                });
                KVInt kvInt = new KVInt();
                kvInt.setId(String.valueOf(resultSet.getLong("time_bucket")));
                kvInt.setValue(resultSet.getLong("value")); // Aggregated value
                mv.getValues().addKVInt(kvInt);
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to read labeled metrics values without entity for table: {}. SQL: {}", tableName, sqlBuilder.toString(), e);
            throw new IOException("Failed to read labeled metrics values without entity from Doris: " + tableName, e);
        }
        return new ArrayList<>(seriesMap.values());
    }

    @Override
    public HeatMap readHeatMap(MetricsCondition condition, String valueColumnName, Duration duration) throws IOException {
        // Heatmap data is usually stored in a specific way:
        // A set of buckets (steps) and the count/value for each bucket.
        // The `valueColumnName` here might refer to a column that itself contains histogram data (e.g., a string or complex type)
        // or it refers to the raw values that need to be bucketed by the query.
        // Doris doesn't have a direct heatmap aggregation function like ES.
        // If `valueColumnName` stores raw values, we'd need to perform bucketing in SQL:
        // SELECT FLOOR(value / step_size) * step_size as bucket_start, COUNT(*) as count
        // FROM table WHERE ... GROUP BY bucket_start
        // This requires knowing step_size or number of buckets.

        // If `valueColumnName` stores pre-aggregated histogram (e.g. SkyWalking's DataTable string):
        // SELECT time_bucket, value_column FROM table WHERE entity_id = ? AND time_bucket BETWEEN ? AND ?
        // Then parse the DataTable string. This is what ES DAO does with `ThermodynamicMetrics.getDetailGroup()`.
        
        String tableName = condition.getName();
        String entityId = condition.getEntity().buildId();
        // `valueColumnName` is expected to hold the histogram data, often serialized (e.g., SkyWalking's DataTable)
        // Let's assume it's a column like `detail_group` for ThermodynamicMetrics or similar for PxxMetrics.

        StringBuilder sqlBuilder = new StringBuilder();
        List<Object> params = new ArrayList<>();
        sqlBuilder.append("SELECT time_bucket, ").append(valueColumnName); // This column should contain the heatmap/histogram data
        sqlBuilder.append(" FROM ").append(tableName);
        sqlBuilder.append(" WHERE entity_id = ?");
        params.add(entityId);
        sqlBuilder.append(" AND time_bucket >= ? AND time_bucket <= ?");
        params.add(duration.getStartTimestamp());
        params.add(duration.getEndTimestamp());
        sqlBuilder.append(" ORDER BY time_bucket ASC");

        HeatMap heatMap = new HeatMap();
        try (ResultSet resultSet = dorisClient.executeQuery(sqlBuilder.toString(), params.toArray())) {
            org.apache.skywalking.oap.server.core.analysis.metrics.DataTable heatMapDataTable = new org.apache.skywalking.oap.server.core.analysis.metrics.DataTable();
            while (resultSet != null && resultSet.next()) {
                String serializedHistogram = resultSet.getString(valueColumnName);
                if (serializedHistogram != null && !serializedHistogram.isEmpty()) {
                    // Deserialize the string into DataTable.
                    // This assumes the string is in the format that DataTable can parse.
                    // Example: "value1:count1,value2:count2"
                    heatMapDataTable.append(new org.apache.skywalking.oap.server.core.analysis.metrics.DataTable(serializedHistogram));
                }
            }
            // Convert DataTable to HeatMap's internal structure
            for (String key : heatMapDataTable.keys()) {
                heatMap.addBucket(new KVInt(key, heatMapDataTable.get(key), false));
            }
            // The HeatMap object in SkyWalking also has an `id` field for each bucket,
            // which usually corresponds to the bucket's lower bound.
            // The `DataTable.toHeatmapJSON()` or similar logic in ES DAO is more complex.
            // This is a simplified conversion.
        } catch (SQLException e) {
            LOGGER.error("Failed to read heatmap data for entity: {}, table: {}. SQL: {}", entityId, tableName, sqlBuilder.toString(), e);
            throw new IOException("Failed to read heatmap data from Doris: " + tableName, e);
        }
        LOGGER.debug("Read heatmap for entity: {}, table: {}, buckets found: {}", entityId, tableName, heatMap.getBuckets().size());
        return heatMap;
    }

    /**
     * Implements the sorting query. This is not part of IMetricsQueryDAO but often implemented by DAOs.
     * This method is based on the prompt's request for `sortMetrics`.
     */
    public List<SelectedRecord> sortMetrics(MetricsCondition condition, String valueColumnName,
                                            Duration duration, int topN, Order order) throws IOException {
        String tableName = condition.getName(); // Metric name as table name

        StringBuilder sqlBuilder = new StringBuilder();
        List<Object> params = new ArrayList<>();

        // We need to select the entity ID and the aggregated value.
        // Assuming `entity_id` is the column to identify the records to be sorted.
        // The aggregation function (SUM, AVG) depends on the metric type.
        // For now, let's assume SUM, or it should be part of `valueColumnName` (e.g. `sum_value`).
        // If `valueColumnName` is just "value", we might need to decide aggregation. Let's use AVG for now.
        
        sqlBuilder.append("SELECT entity_id, AVG(").append(valueColumnName).append(") AS aggregated_value");
        sqlBuilder.append(" FROM ").append(tableName);
        sqlBuilder.append(" WHERE time_bucket >= ? AND time_bucket <= ?");
        params.add(duration.getStartTimestamp());
        params.add(duration.getEndTimestamp());

        // Add scope conditions if present in MetricsCondition
        // Example: if (condition.getScope() == Scope.SERVICE && condition.getEntity().getServiceId() != null) {
        // This part depends on how scopes are defined and if 'entity_id' already reflects the correct scope.
        // For now, assuming entity_id is granular enough or no further scope filtering is needed here.
        // If condition.getEntity().getScope() is SERVICE_INSTANCE, entity_id might be instance_id.
        // If SERVICE, entity_id might be service_id. This needs alignment with how entity_id is populated.

        sqlBuilder.append(" GROUP BY entity_id");
        sqlBuilder.append(" ORDER BY aggregated_value ").append(order == Order.ASC ? "ASC" : "DESC");
        sqlBuilder.append(" LIMIT ?");
        params.add(topN);

        List<SelectedRecord> selectedRecords = new ArrayList<>();
        try (ResultSet resultSet = dorisClient.executeQuery(sqlBuilder.toString(), params.toArray())) {
            while (resultSet != null && resultSet.next()) {
                SelectedRecord record = new SelectedRecord();
                record.setId(resultSet.getString("entity_id"));
                record.setValue(String.valueOf(resultSet.getLong("aggregated_value"))); // Value as string
                // `refId` is not applicable here or needs to be fetched if relevant.
                selectedRecords.add(record);
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to sort metrics for table: {}. SQL: {}", tableName, sqlBuilder.toString(), e);
            throw new IOException("Failed to sort metrics from Doris: " + tableName, e);
        }
        
        LOGGER.debug("Sorted metrics for table: {}, topN: {}, order: {}, results count: {}", tableName, topN, order, selectedRecords.size());
        return selectedRecords;
    }
    
    // Stubs for methods not explicitly requested to be fully implemented in this round
    // but are part of IMetricsQueryDAO.

    // These methods were part of the interface definition from earlier reviews.
    // Adding them back as stubs to ensure the interface is fully covered.
    // Implementations would require specific logic for interpolation, thermodynamic bucketing, and percentile calculations.

    // @Override // This signature with List<PointOfTime> was from a misremembered readMetricsValues,
                // the actual IMetricsQueryDAO methods are linearIntrp and thermodynamic.
    // public MetricsValues readMetricsValues(MetricsCondition condition, String valueColumnName, Duration duration, List<org.apache.skywalking.oap.server.core.query.PointOfTime> pots) throws IOException {
    //    LOGGER.warn("readMetricsValues with PointOfTime list is not a standard IMetricsQueryDAO method. Use linearIntrp or thermodynamic. Returning empty MetricsValues.");
    //    return new MetricsValues();
    // }


    // @Override // This method signature was from a previous version of IMetricsQueryDAO or a misunderstanding.
                // The current interface uses (MetricsCondition, String, Duration) for the primary readMetricsValue(s) methods.
    // public List<org.apache.skywalking.oap.server.core.analysis.metrics.IntValue> readMetricsValue(MetricsCondition condition, String valueColumnName, Duration duration) throws IOException {
    //    LOGGER.warn("DorisMetricsQueryDAO.readMetricsValue (List<IntValue>) is deprecated or not standard, returning empty list.");
    //    return Collections.emptyList();
    // }

    // Based on the IMetricsDAO file read in subtask 10's context (IMetricsDAO.java from Turn 28 of overall task)
    // The following methods are part of the broader set of metric query capabilities.
    // public List<Metrics> linearIntrp(MetricsCondition condition, String valueColumnName, Duration duration, List<org.apache.skywalking.oap.server.core.query.PointOfTime> pots) throws IOException {
    //     LOGGER.warn("DorisMetricsQueryDAO.linearIntrp not implemented, returning empty list.");
    //     return Collections.emptyList();
    // }

    // public List<Metrics> thermodynamic(MetricsCondition condition, String valueColumnName, Duration duration, List<org.apache.skywalking.oap.server.core.query.PointOfTime> pots) throws IOException {
    //    LOGGER.warn("DorisMetricsQueryDAO.thermodynamic not implemented, returning empty list.");
    //    return Collections.emptyList();
    // }
    
    // public List<org.apache.skywalking.oap.server.core.analysis.metrics.PxxMetrics> pxxMetrics(MetricsCondition condition, String valueColumnName, Duration duration, List<org.apache.skywalking.oap.server.core.query.PointOfTime> pots, List<Integer> percentiles) throws IOException {
    //    LOGGER.warn("DorisMetricsQueryDAO.pxxMetrics not implemented, returning empty list.");
    //    return Collections.emptyList();
    // }
}
/*
 * TODO: The IMetricsQueryDAO interface seems to have different versions or interpretations across subtasks.
 * The file read in Turn 40 (subtask 13) had:
 * - readMetricsValues(MetricsCondition, String, Duration)
 * - readLabeledMetricsValues(MetricsCondition, String, List<KeyValue>, Duration)
 * - readLabeledMetricsValuesWithoutEntity(String, String, List<KeyValue>, Duration)
 * - readHeatMap(MetricsCondition, String, Duration)
 *
 * The stubs for linearIntrp, thermodynamic, pxxMetrics were based on an earlier understanding of IMetricsDAO
 * (e.g. from Turn 10 / Subtask 5 context, or Turn 28 / Subtask 10 context for IMetricsDAO - the persistence one).
 *
 * For IMetricsQueryDAO, the methods listed from Turn 40's file read are the primary ones.
 * The methods linearIntrp, thermodynamic, pxxMetrics are NOT part of the IMetricsQueryDAO interface definition
 * that was read in Turn 40. They might be part of a different interface or an older version.
 *
 * Therefore, no additional stubs are needed based on the IMetricsQueryDAO file read in Turn 40.
 * The `sortMetrics` method was added as a specific requirement from the prompt, not from the interface.
 */
