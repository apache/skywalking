package org.apache.skywalking.oap.server.storage.plugin.doris.util;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;
import org.apache.skywalking.oap.server.storage.plugin.doris.client.DorisClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DorisDAOUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(DorisDAOUtils.class);

    private DorisDAOUtils() {
    }

    public static Map<String, Object> get(DorisClient dorisClient, String modelName, String id) throws IOException {
        String tableName = modelName;
        String sql = "SELECT * FROM " + tableName + " WHERE id = ?";
        LOGGER.debug("Executing SQL: {} with id: {}", sql, id);
        try (ResultSet resultSet = dorisClient.executeQuery(sql, id)) {
            if (resultSet != null && resultSet.next()) {
                Map<String, Object> entity = new HashMap<>();
                ResultSetMetaData metaData = resultSet.getMetaData();
                int columnCount = metaData.getColumnCount();
                for (int i = 1; i <= columnCount; i++) {
                    entity.put(metaData.getColumnLabel(i), resultSet.getObject(i));
                }
                return entity;
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to get entity by id: {} from table: {}. SQL: {}", id, tableName, sql, e);
            throw new IOException("Failed to get entity from Doris: " + tableName + ", id: " + id, e);
        }
        return null;
    }

    public static Map<String, Object> getByValue(DorisClient dorisClient, String modelName, String valueCName, String value) throws IOException {
        String tableName = modelName;
        String sql = "SELECT * FROM " + tableName + " WHERE " + valueCName + " = ?";
        LOGGER.debug("Executing SQL: {} with value: {}", sql, value);
        try (ResultSet resultSet = dorisClient.executeQuery(sql, value)) {
            if (resultSet != null && resultSet.next()) {
                Map<String, Object> entity = new HashMap<>();
                ResultSetMetaData metaData = resultSet.getMetaData();
                int columnCount = metaData.getColumnCount();
                for (int i = 1; i <= columnCount; i++) {
                    entity.put(metaData.getColumnLabel(i), resultSet.getObject(i));
                }
                return entity;
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to get entity by value: {} from table: {}, column: {}. SQL: {}", value, tableName, valueCName, sql, e);
            throw new IOException("Failed to get entity by value from Doris: " + tableName + ", column: " + valueCName, e);
        }
        return null;
    }

    public static Map<String, Object> getByValues(DorisClient dorisClient, String modelName, Map<String, String> criteria) throws IOException {
        String tableName = modelName;
        if (criteria == null || criteria.isEmpty()) {
            LOGGER.warn("Attempted to call getByValues with empty criteria map for model: {}", modelName);
            return null;
        }

        StringBuilder sqlBuilder = new StringBuilder("SELECT * FROM ").append(tableName).append(" WHERE ");
        List<Object> params = new ArrayList<>();
        List<String> conditions = new ArrayList<>();

        for (Map.Entry<String, String> entry : criteria.entrySet()) {
            conditions.add(entry.getKey() + " = ?");
            params.add(entry.getValue());
        }
        sqlBuilder.append(String.join(" AND ", conditions));
        String sql = sqlBuilder.toString();

        LOGGER.debug("Executing SQL: {} with params: {}", sql, params);
        try (ResultSet resultSet = dorisClient.executeQuery(sql, params.toArray())) {
            if (resultSet != null && resultSet.next()) {
                Map<String, Object> entity = new HashMap<>();
                ResultSetMetaData metaData = resultSet.getMetaData();
                int columnCount = metaData.getColumnCount();
                for (int i = 1; i <= columnCount; i++) {
                    entity.put(metaData.getColumnLabel(i), resultSet.getObject(i));
                }
                if (resultSet.next()) {
                    LOGGER.warn("getByValues (Map) for model {} with criteria {} returned multiple rows. Returning the first one.", modelName, criteria);
                }
                return entity;
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to get entity by criteria: {} from table: {}. SQL: {}", criteria, tableName, sql, e);
            throw new IOException("Failed to get entity by criteria from Doris: " + tableName, e);
        }
        return null;
    }

    public static List<Map<String, Object>> getByValues(DorisClient dorisClient, String modelName, String valueCName, List<String> values) throws IOException {
        String tableName = modelName;
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> resultList = new ArrayList<>();
        StringBuilder sqlBuilder = new StringBuilder("SELECT * FROM ").append(tableName).append(" WHERE ").append(valueCName).append(" IN (");
        for (int i = 0; i < values.size(); i++) {
            sqlBuilder.append("?");
            if (i < values.size() - 1) {
                sqlBuilder.append(", ");
            }
        }
        sqlBuilder.append(")");
        String sql = sqlBuilder.toString();

        LOGGER.debug("Executing SQL: {} with values: {}", sql, values);
        try (ResultSet resultSet = dorisClient.executeQuery(sql, values.toArray())) {
            while (resultSet != null && resultSet.next()) {
                Map<String, Object> entity = new HashMap<>();
                ResultSetMetaData metaData = resultSet.getMetaData();
                int columnCount = metaData.getColumnCount();
                for (int i = 1; i <= columnCount; i++) {
                    entity.put(metaData.getColumnLabel(i), resultSet.getObject(i));
                }
                resultList.add(entity);
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to get entities by values: {} from table: {}, column: {}. SQL: {}", values, tableName, valueCName, sql, e);
            throw new IOException("Failed to get entities by values from Doris: " + tableName + ", column: " + valueCName, e);
        }
        return resultList;
    }

    public static InsertRequest prepareInsertRequest(DorisClient dorisClient, String modelName, Map<String, Object> newEntity, StorageBuilder<?> storageBuilder) {
        LOGGER.info("Preparing single insert for model: {}", modelName);
        // StorageBuilder is not used in this generic version, but kept for signature consistency if DAOs using it call this.
        return new DorisInsertRequest(modelName, newEntity, dorisClient);
    }

    public static UpdateRequest prepareUpdateRequest(DorisClient dorisClient, String modelName, String id, Map<String, Object> updateEntity, StorageBuilder<?> storageBuilder) {
        LOGGER.info("Preparing single update for model: {}, id: {}", modelName, id);
        // StorageBuilder is not used here.
        return new DorisUpdateRequest(modelName, id, updateEntity, dorisClient);
    }

    public static void delete(DorisClient dorisClient, String modelName, String id) throws IOException {
        String tableName = modelName;
        String sql = "DELETE FROM " + tableName + " WHERE id = ?";
        LOGGER.debug("Executing SQL: {} with id: {}", sql, id);
        try {
            int affectedRows = dorisClient.executeUpdate(sql, id);
            if (affectedRows == 0) {
                LOGGER.warn("No rows affected when deleting from table: {} with id: {}. SQL: {}", tableName, id, sql);
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to delete entity by id: {} from table: {}. SQL: {}", id, tableName, sql, e);
            throw new IOException("Failed to delete entity from Doris: " + tableName + ", id: " + id, e);
        }
    }

    // Static inner classes for InsertRequest and UpdateRequest
    public static class DorisInsertRequest implements InsertRequest {
        private final String modelName;
        private final Map<String, Object> entity;
        private final DorisClient dorisClient;

        public DorisInsertRequest(String modelName, Map<String, Object> entity, DorisClient dorisClient) {
            this.modelName = modelName;
            this.entity = entity;
            this.dorisClient = dorisClient;
        }

        @Override
        public void execute() throws IOException {
            String tableName = modelName;
            StringBuilder sql = new StringBuilder("INSERT INTO ").append(tableName).append(" (");
            List<String> columns = new ArrayList<>(entity.keySet());
            sql.append(String.join(", ", columns));
            sql.append(") VALUES (");
            sql.append(columns.stream().map(col -> "?").collect(Collectors.joining(", ")));
            sql.append(")");

            Object[] params = columns.stream().map(entity::get).toArray();

            try {
                LOGGER.debug("Executing SQL: {} with params: {}", sql.toString(), params);
                dorisClient.executeUpdate(sql.toString(), params);
            } catch (SQLException e) {
                LOGGER.error("Failed to execute insert SQL: {} for model: {}", sql.toString(), modelName, e);
                throw new IOException("Failed to insert data into Doris for model " + modelName, e);
            }
        }
    }

    public static class DorisUpdateRequest implements UpdateRequest {
        private final String modelName;
        private final String id;
        private final Map<String, Object> entity;
        private final DorisClient dorisClient;

        public DorisUpdateRequest(String modelName, String id, Map<String, Object> entity, DorisClient dorisClient) {
            this.modelName = modelName;
            this.id = id;
            this.entity = entity;
            this.dorisClient = dorisClient;
        }

        @Override
        public void execute() throws IOException {
            String tableName = modelName;
            StringBuilder sql = new StringBuilder("UPDATE ").append(tableName).append(" SET ");
            List<String> columnsToUpdate = new ArrayList<>(entity.keySet());
            sql.append(columnsToUpdate.stream().map(col -> col + " = ?").collect(Collectors.joining(", ")));
            sql.append(" WHERE id = ?"); 

            List<Object> paramsList = columnsToUpdate.stream().map(entity::get).collect(Collectors.toList());
            paramsList.add(this.id);
            Object[] params = paramsList.toArray();

            try {
                LOGGER.debug("Executing SQL: {} with params: {}", sql.toString(), params);
                dorisClient.executeUpdate(sql.toString(), params);
            } catch (SQLException e) {
                LOGGER.error("Failed to execute update SQL: {} for model: {}, id: {}", sql.toString(), modelName, id, e);
                throw new IOException("Failed to update data in Doris for model " + modelName + ", id " + id, e);
            }
        }
    }
}
