package org.apache.skywalking.oap.server.storage.plugin.doris.client;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.apache.skywalking.oap.server.storage.plugin.doris.StorageModuleDorisConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DorisClientTest {

    @Mock
    private StorageModuleDorisConfig mockConfig;

    @Mock
    private Connection mockConnection;

    private DorisClient dorisClient;

    private MockedStatic<DriverManager> driverManagerMockedStatic;

    @BeforeEach
    void setUp() throws SQLException {
        // Mock config values
        when(mockConfig.getHost()).thenReturn("localhost");
        when(mockConfig.getPort()).thenReturn(9030);
        when(mockConfig.getDatabase()).thenReturn("test_db");
        when(mockConfig.getUser()).thenReturn("test_user");
        when(mockConfig.getPassword()).thenReturn("test_password");

        dorisClient = new DorisClient(mockConfig);

        // Start mocking DriverManager
        driverManagerMockedStatic = Mockito.mockStatic(DriverManager.class);
        driverManagerMockedStatic.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                                 .thenReturn(mockConnection);
    }

    @AfterEach
    void tearDown() {
        // Close the static mock
        if (driverManagerMockedStatic != null) {
            driverManagerMockedStatic.close();
        }
        // Ensure client disconnects if connection was made
        dorisClient.disconnect();
    }

    @Test
    void testConnectAndDisconnect() throws SQLException {
        // Expected JDBC URL
        String expectedJdbcUrl = "jdbc:mysql://localhost:9030/test_db?useSSL=false";

        // Call connect
        dorisClient.connect();

        // Verify DriverManager.getConnection was called with the correct parameters
        driverManagerMockedStatic.verify(() -> DriverManager.getConnection(
            eq(expectedJdbcUrl),
            eq("test_user"),
            eq("test_password")
        ));

        // Call disconnect
        dorisClient.disconnect();

        // Verify the connection was closed
        verify(mockConnection, times(1)).close();
    }

    @Test
    void testConnectFailure() throws SQLException {
        // Expected JDBC URL
        String expectedJdbcUrl = "jdbc:mysql://localhost:9030/test_db?useSSL=false";

        // Simulate SQLException during getConnection
        driverManagerMockedStatic.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                                 .thenThrow(new SQLException("Connection failed"));

        // Assert that connect throws SQLException
        org.junit.jupiter.api.Assertions.assertThrows(SQLException.class, () -> {
            dorisClient.connect();
        });

        // Verify DriverManager.getConnection was still called
        driverManagerMockedStatic.verify(() -> DriverManager.getConnection(
            eq(expectedJdbcUrl),
            eq("test_user"),
            eq("test_password")
        ));
    }

    @Test
    void testDisconnectWithoutConnection() {
        // Call disconnect without calling connect first
        dorisClient.disconnect();
        // No exception should be thrown, and close should not be called on mockConnection as it was never set.
        verify(mockConnection, times(0)).close();
    }
}
