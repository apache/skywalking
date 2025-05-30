package org.apache.skywalking.oap.server.storage.plugin.doris.query;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import org.apache.skywalking.oap.server.core.query.PointOfTime;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.Entity;
import org.apache.skywalking.oap.server.core.query.input.MetricsCondition;
import org.apache.skywalking.oap.server.core.query.type.MetricsValues;
import org.apache.skywalking.oap.server.storage.plugin.doris.client.DorisClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DorisMetricsQueryDAOTest {

    @Mock
    private DorisClient mockDorisClient;

    @Mock
    private ResultSet mockResultSet;

    private DorisMetricsQueryDAO dorisMetricsQueryDAO;

    @BeforeEach
    void setUp() {
        dorisMetricsQueryDAO = new DorisMetricsQueryDAO(mockDorisClient);
    }

    @Test
    void testReadMetricsValues() throws IOException, SQLException {
        // Prepare input data
        MetricsCondition condition = new MetricsCondition();
        condition.setName("service_cpm"); // Example metric name
        Entity entity = new Entity();
        entity.setServiceName("test_service"); // Example entity
        condition.setEntity(entity);

        String valueColumnName = "value_column";
        Duration duration = new Duration();
        duration.setStart("2024-01-01 000000"); // Using string format for simplicity, will be converted to long
        duration.setEnd("2024-01-01 010000");
        // Assuming Duration.getStartTimestamp() and getEndTimestamp() are implemented to parse these
        // For the purpose of this test, the exact timestamp values don't matter as much as the generated SQL.
        // Let's assume getStartTimestamp() -> 1704067200000L and getEndTimestamp() -> 1704070800000L

        PointOfTime pot = new PointOfTime(); // Simplified: using one PoT.
        // Realistically, PoT values would be derived from duration for query.
        // The current DAO impl doesn't directly use PoT values in its example SQL,
        // but uses duration.getStart/EndTimestamp().

        // Mock DorisClient's executeQuery to return a mock ResultSet
        when(mockDorisClient.executeQuery(anyString())).thenReturn(mockResultSet);

        // Call the method to be tested
        MetricsValues result = dorisMetricsQueryDAO.readMetricsValues(condition, valueColumnName, duration, Collections.singletonList(pot));

        // Verify the result is not null (actual content verification would depend on ResultSet processing)
        assertNotNull(result);

        // Verify the SQL passed to DorisClient.executeQuery
        // This expected SQL is based on the current stubbed implementation in DorisMetricsQueryDAO
        String expectedSql = "SELECT " + valueColumnName + " FROM metrics_all" +
                             " WHERE id = '" + entity.buildId() + "'" +
                             " AND name = '" + condition.getName() + "'" +
                             " AND time_bucket >= " + duration.getStartTimestamp() + // This assumes getStartTimestamp works
                             " AND time_bucket <= " + duration.getEndTimestamp() +   // This assumes getEndTimestamp works
                             " ORDER BY time_bucket";

        verify(mockDorisClient).executeQuery(expectedSql);
    }
}
