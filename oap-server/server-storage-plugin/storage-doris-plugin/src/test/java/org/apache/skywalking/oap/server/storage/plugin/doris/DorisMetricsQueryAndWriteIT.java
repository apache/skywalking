package org.apache.skywalking.oap.server.storage.plugin.doris;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.Downsampling;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.LongValueHolder;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.Entity;
import org.apache.skywalking.oap.server.core.query.input.MetricsCondition;
import org.apache.skywalking.oap.server.core.query.type.KVInt;
import org.apache.skywalking.oap.server.core.query.type.MetricsValues;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelColumn;
import org.apache.skywalking.oap.server.core.storage.model.SQLDatabaseModelExtension;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.storage.plugin.doris.dao.DorisBatchDAO;
import org.apache.skywalking.oap.server.storage.plugin.doris.dao.DorisMetricsDAO;
import org.apache.skywalking.oap.server.storage.plugin.doris.query.DorisMetricsQueryDAO;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;


// A simple Metrics implementation for testing
class TestServiceCpmMetrics extends Metrics implements LongValueHolder {
    private long value;
    private String serviceId; // Represents the actual service ID

    public TestServiceCpmMetrics(String serviceId, long value, long timeBucket) {
        this.serviceId = serviceId;
        this.value = value;
        setTimeBucket(timeBucket);
    }

    @Override
    public String id() {
        // This ID is used as the primary key for the row in the DB.
        // Format needs to be compatible with DorisMetricsDAO.convertMetricsToMap entity_id heuristic if model.isServiceScope() is true.
        // Heuristic: entityId = fullId.substring(0, fullId.indexOf("."));
        // So, id should be "serviceId.timeBucket"
        return this.serviceId + "." + getTimeBucket();
    }
    
    public String getServiceId() { // Custom getter for the raw serviceId
        return serviceId;
    }

    @Override
    public Metrics newInstance() {
        return new TestServiceCpmMetrics(this.serviceId, this.value, getTimeBucket());
    }

    @Override
    public void combine(Metrics next) {
        this.value += ((TestServiceCpmMetrics) next).value;
    }

    @Override
    public void calculate() { /* no op for simple test */ }

    @Override
    public StorageID getStorageID() {
        // This method is crucial. It defines how the metric is identified for storage purposes,
        // especially for constructing entity IDs.
        // For a service-scoped metric, the first part of the ID is typically the service_id.
        return new StorageID().append(Model.SERVICE_ID_COLUMNNAME, this.serviceId);
    }
    
    @Override
    public long getValue() {
        return value;
    }

    @Override
    public void setValue(long value) {
        this.value = value;
    }

    @Override
    public int remoteHashCode() { return 0; } 
}


@ExtendWith(MockitoExtension.class) 
public class DorisMetricsQueryAndWriteIT extends DorisStoragePluginITBase {

    private DorisMetricsDAO metricsDAO;
    private DorisMetricsQueryDAO metricsQueryDAO;
    private DorisBatchDAO batchDAO;

    @BeforeEach
    public void setUpDAOs() {
        metricsDAO = new DorisMetricsDAO(getDorisClient());
        metricsQueryDAO = new DorisMetricsQueryDAO(getDorisClient());
        batchDAO = new DorisBatchDAO(getDorisClient(), getDorisConfig()); 
    }

    @Test
    public void testWriteAndReadMetrics() throws IOException {
        long currentTimeMillis = System.currentTimeMillis();
        // Time bucket in YYYYMMDDHHMM format
        long timeBucket = Long.parseLong(new org.apache.skywalking.oap.server.core.analysis.TimeBucket(currentTimeMillis, Downsampling.Minute).toString()); 

        String testServiceId = "test-service-id-cpm";
        long cpmValue = 150L;

        TestServiceCpmMetrics metrics = new TestServiceCpmMetrics(testServiceId, cpmValue, timeBucket);
        
        List<ModelColumn> columns = List.of(
            new ModelColumn("id", String.class, true, true, true, 255),
            new ModelColumn("metric_name", String.class, false, true, false, 255),
            new ModelColumn(Model.ENTITY_ID, String.class, false, true, false, 255), // Standard entity_id column name
            new ModelColumn("value", Long.class, false, true, false, 0),
            new ModelColumn("time_bucket", Long.class, false, true, true, 0),
            new ModelColumn("timestamp", Long.class, false, true, false, 0)
        );

        // Create a Model that accurately reflects how TestServiceCpmMetrics would be stored.
        // Crucially, set isServiceScope = true for the entity_id heuristic in convertMetricsToMap to work.
        Model model = new Model(
            "metrics_all", 
            columns,
            Collections.emptyList(), 
            true, // isServiceScope = true
            Downsampling.Minute, 
            true, 
            "core.TestServiceCpmMetrics",
            new SQLDatabaseModelExtension() // Add SQLDatabaseModelExtension
        );

        // 1. Write metrics
        InsertRequest insertRequest = metricsDAO.prepareBatchInsert(model, metrics, null);
        batchDAO.insert(insertRequest); 

        try {
            Thread.sleep(1500); // Increased delay for Doris
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 2. Read metrics back
        MetricsCondition condition = new MetricsCondition();
        condition.setName(model.getName()); // Table name "metrics_all"
        
        Entity entity = new Entity();
        // For service-scoped metrics, Entity.id should be the service ID.
        entity.setServiceId(testServiceId); 
        entity.setNormal(true); // Or false if it's not a normal service, but true is typical.
        condition.setEntity(entity); 

        Duration duration = new Duration();
        duration.setStartTimestamp(timeBucket); // Query for the exact time bucket
        duration.setEndTimestamp(timeBucket);

        // The valueColumnName in metrics_all table is "value".
        MetricsValues queriedValues = metricsQueryDAO.readMetricsValues(condition, "value", duration);

        // 3. Assert
        Assertions.assertNotNull(queriedValues, "QueriedValues should not be null");
        Assertions.assertNotNull(queriedValues.getValues(), "QueriedValues.getValues() should not be null");
        Assertions.assertFalse(queriedValues.getValues().getValues().isEmpty(), 
            "No metric values found for serviceId=" + testServiceId + ", timeBucket=" + timeBucket + 
            ". Queried entityId: " + entity.buildId());
        
        boolean found = false;
        for (KVInt kv : queriedValues.getValues().getValues()) {
            if (Long.parseLong(kv.getId()) == timeBucket) { 
                Assertions.assertEquals(cpmValue, kv.getValue(), "Queried CPM value does not match original.");
                found = true;
                break;
            }
        }
        Assertions.assertTrue(found, "Metric for serviceId=" + testServiceId + ", timeBucket=" + timeBucket + " was not found.");
    }
}
