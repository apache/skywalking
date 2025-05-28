package org.apache.skywalking.oap.server.storage.plugin.doris.dao;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.metrics.DoubleValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.LongValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.StringValueHolder;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.SessionCacheCallback;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;
import org.apache.skywalking.oap.server.storage.plugin.doris.client.DorisClient;
import org.apache.skywalking.oap.server.storage.plugin.doris.util.DorisDAOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DorisMetricsDAO implements IMetricsDAO {

    private static final Logger LOGGER = LoggerFactory.getLogger(DorisMetricsDAO.class);
    private final DorisClient dorisClient;

    public DorisMetricsDAO(DorisClient dorisClient) {
        this.dorisClient = dorisClient;
    }

    @Override
    public List<Metrics> multiGet(Model model, List<Metrics> metrics) throws Exception {
        LOGGER.warn("DorisMetricsDAO.multiGet not implemented yet. Model: {}", model.getName());
        return Collections.emptyList();
    }
    
    @Override
    public InsertRequest prepareBatchInsert(Model model, Metrics metrics, SessionCacheCallback callback) throws IOException {
        Map<String, Object> entityMap = convertMetricsToMap(metrics, model);
        if (callback != null) {
            LOGGER.debug("SessionCacheCallback provided for insert on model {}, metric id {}", model.getName(), metrics.id().build());
        }
        // StorageBuilder is not used by DorisDAOUtils.prepareInsertRequest currently.
        return DorisDAOUtils.prepareInsertRequest(dorisClient, model.getName(), entityMap, null);
    }

    @Override
    public UpdateRequest prepareBatchUpdate(Model model, Metrics metrics, SessionCacheCallback callback) throws IOException {
        Map<String, Object> entityMap = convertMetricsToMap(metrics, model);
        String id = metrics.id().build(); 
        if (callback != null) {
            LOGGER.debug("SessionCacheCallback provided for update on model {}, metric id {}", model.getName(), id);
        }
        // StorageBuilder is not used by DorisDAOUtils.prepareUpdateRequest currently.
        return DorisDAOUtils.prepareUpdateRequest(dorisClient, model.getName(), id, entityMap, null);
    }

    /**
     * Converts a Metrics object to a Map based on the subtask's table structure assumption.
     * Table structure: id, name, entity_id, value, time_bucket, timestamp
     * @param metrics The Metrics object to convert.
     * @param model   The Model object, used for downsampling info and model/table name.
     * @return A map representing the metrics data for Doris.
     */
    private Map<String, Object> convertMetricsToMap(Metrics metrics, Model model) {
        Map<String, Object> map = new HashMap<>();

        // 1. `id` (String, primary key)
        map.put("id", metrics.id().build());

        // 2. `name` (String, from `metrics.getName()` - but Metrics.getName() does not exist)
        // Using model.getName() as the content for the 'name' column.
        // This means the 'name' column will store the type/class of the metric (e.g., "service_cpm", "endpoint_p99").
        map.put("name", model.getName());

        // 3. `entity_id` (String, from `metrics.getEntityId()` - but Metrics.getEntityId() does not exist)
        // The entity ID is part of metrics.id(). For generic metrics, full ID might be complex.
        // Example: Service ID for service-level metrics, Endpoint ID for endpoint metrics.
        // This requires knowledge of how StorageID is structured for different metric types.
        // As a generic placeholder, we'll use a simplified extraction.
        // If StorageID is "service_id.instance_id.endpoint_name", then entity_id might be "service_id".
        // This is a simplification; real entity ID extraction can be complex.
        String fullId = metrics.id().build();
        String entityId = fullId; // Default to full ID as a safe, albeit potentially too broad, placeholder.
        // A common pattern for service-related metrics is that the ID starts with the service ID.
        // e.g., ServiceID + "." + other parts. If so, this would be a simple way to get it.
        // This heuristic is highly dependent on the actual ID structure.
        if (model.isServiceScope()) { // Example: if we know it's service-scoped
             // Attempt to get a more specific entity_id if possible, e.g. by convention from StorageID parts.
             // For instance, if service_id is the first part of the ID string.
             // This is still a guess. The StorageID structure is not universally defined at this generic level.
            String[] idParts = fullId.split("\\.", 2);
            if (idParts.length > 0) {
                entityId = idParts[0];
            }
        }
        // For other scopes (instance, endpoint), similar logic would be needed but is more complex.
        map.put("entity_id", entityId);


        // 4. `value` (long or double, from `metrics.getValue()`)
        if (metrics instanceof LongValueHolder) {
            map.put("value", ((LongValueHolder) metrics).getValue());
        } else if (metrics instanceof IntValueHolder) {
            map.put("value", Long.valueOf(((IntValueHolder) metrics).getValue())); // Store int as long in DB
        } else if (metrics instanceof DoubleValueHolder) {
            map.put("value", ((DoubleValueHolder) metrics).getValue());
        } else if (metrics instanceof StringValueHolder) {
            // Storing string value. The 'value' column in Doris must be of a compatible type (e.g., VARCHAR or TEXT).
            map.put("value", ((StringValueHolder) metrics).getValue());
            LOGGER.debug("Storing StringValueHolder's value in 'value' column for metric id {}. Ensure DB schema compatibility.", metrics.id().build());
        } else {
            LOGGER.warn("Metrics type not recognized for 'value' extraction: {} for metric id {}", metrics.getClass().getName(), metrics.id().build());
            map.put("value", null); // Or some default / error indicator
        }

        // 5. `time_bucket` (long, from `metrics.getTimeBucket()`)
        map.put("time_bucket", metrics.getTimeBucket());

        // 6. `timestamp` (long, from `metrics.getTimestamp()`)
        // Calculated using time_bucket and model's downsampling.
        map.put("timestamp", TimeBucket.getTimestamp(metrics.getTimeBucket(), model.getDownsampling()));
        
        LOGGER.debug("Converted metrics (ID: {}) for model {} to map: {}", metrics.id().build(), model.getName(), map);
        return map;
    }
}
