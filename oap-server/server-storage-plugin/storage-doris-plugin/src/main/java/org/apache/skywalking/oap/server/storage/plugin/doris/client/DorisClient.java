package org.apache.skywalking.oap.server.storage.plugin.doris.client;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.apache.skywalking.oap.server.storage.plugin.doris.StorageModuleDorisConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DorisClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(DorisClient.class);

    private final StorageModuleDorisConfig config;
    private Connection connection;

    public DorisClient(StorageModuleDorisConfig config) {
        this.config = config;
    }

    public void connect() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            LOGGER.error("Failed to load MySQL JDBC driver.", e);
            throw new SQLException("Failed to load MySQL JDBC driver.", e);
        }

        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false",
                                       config.getHost(),
                                       config.getPort(),
                                       config.getDatabase());
        try {
            connection = DriverManager.getConnection(jdbcUrl, config.getUser(), config.getPassword());
            LOGGER.info("Successfully connected to Doris: {}", jdbcUrl);
        } catch (SQLException e) {
            LOGGER.error("Failed to connect to Doris: {}", jdbcUrl, e);
            throw e;
        }
    }

    public void disconnect() {
        if (connection != null) {
            try {
                connection.close();
                LOGGER.info("Successfully disconnected from Doris.");
            } catch (SQLException e) {
                LOGGER.error("Failed to disconnect from Doris.", e);
            } finally {
                connection = null;
            }
        }
    }

    public ResultSet executeQuery(String sql) throws SQLException {
        if (connection == null) {
            throw new SQLException("Not connected to Doris.");
        }
        Statement statement = connection.createStatement();
        return statement.executeQuery(sql);
    }

    public int executeUpdate(String sql, Object... params) throws SQLException {
        if (connection == null) {
            throw new SQLException("Not connected to Doris.");
        }
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    preparedStatement.setObject(i + 1, params[i]);
                }
            }
            return preparedStatement.executeUpdate();
        }
    }
}
