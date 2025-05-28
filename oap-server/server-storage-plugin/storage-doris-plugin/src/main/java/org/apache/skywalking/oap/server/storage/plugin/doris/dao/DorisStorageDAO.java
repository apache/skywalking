package org.apache.skywalking.oap.server.storage.plugin.doris.dao;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.UpdateRequest;
import org.apache.skywalking.oap.server.storage.plugin.doris.client.DorisClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DorisStorageDAO implements StorageDAO {

    private static final Logger LOGGER = LoggerFactory.getLogger(DorisStorageDAO.class);
    private final DorisClient dorisClient;

    public DorisStorageDAO(DorisClient dorisClient) {
        this.dorisClient = dorisClient;
    }

    @Override
    public Map<String, Object> get(String modelName, String id) throws IOException {
        LOGGER.warn("DorisStorageDAO.get not implemented");
        throw new UnsupportedOperationException("DorisStorageDAO.get not implemented");
    }

    @Override
    public Map<String, Object> getByValue(String modelName, String valueCName, String value) throws IOException {
        LOGGER.warn("DorisStorageDAO.getByValue not implemented");
        throw new UnsupportedOperationException("DorisStorageDAO.getByValue not implemented");
    }

    @Override
    public Map<String, Object> getByValues(String modelName, Map<String, String> values) throws IOException {
        LOGGER.warn("DorisStorageDAO.getByValues not implemented");
        throw new UnsupportedOperationException("DorisStorageDAO.getByValues not implemented");
    }

    @Override
    public List<Map<String, Object>> getByValues(String modelName, String valueCName, List<String> values) throws IOException {
        LOGGER.warn("DorisStorageDAO.getByValues not implemented");
        throw new UnsupportedOperationException("DorisStorageDAO.getByValues not implemented");
    }

    @Override
    public InsertRequest prepareBatchInsert(String modelName, Map<String, Object> newEntity, StorageBuilder storageBuilder) throws IOException {
        // This method is typically for batching, for a single insert, we can directly execute.
        // For a real batch, a different approach would be needed with DorisClient.
        LOGGER.info("Preparing single insert for model: {}", modelName);
        return new DorisInsertRequest(modelName, newEntity);
    }

    @Override
    public UpdateRequest prepareBatchUpdate(String modelName, String id, Map<String, Object> updateEntity, StorageBuilder storageBuilder) throws IOException {
        // Similar to insert, this is for a single update.
        LOGGER.info("Preparing single update for model: {}, id: {}", modelName, id);
        return new DorisUpdateRequest(modelName, id, updateEntity);
    }

    @Override
    public void delete(String modelName, String id) throws IOException {
        LOGGER.warn("DorisStorageDAO.delete not implemented");
        throw new UnsupportedOperationException("DorisStorageDAO.delete not implemented");
    }

    // Example implementation for DorisInsertRequest
    private class DorisInsertRequest implements InsertRequest {
        private final String modelName;
        private final Map<String, Object> entity;

        public DorisInsertRequest(String modelName, Map<String, Object> entity) {
            this.modelName = modelName;
            this.entity = entity;
        }

        @Override
        public void execute() throws IOException {
            // Assuming modelName is the table name
            String tableName = modelName;
            StringBuilder sql = new StringBuilder("INSERT INTO ").append(tableName).append(" (");
            List<String> columns = entity.keySet().stream().collect(Collectors.toList());
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

    // Example implementation for DorisUpdateRequest
    private class DorisUpdateRequest implements UpdateRequest {
        private final String modelName;
        private final String id;
        private final Map<String, Object> entity;


        public DorisUpdateRequest(String modelName, String id, Map<String, Object> entity) {
            this.modelName = modelName;
            this.id = id; // Assuming 'id' is the primary key column name.
            this.entity = entity;
        }

        @Override
        public void execute() throws IOException {
            String tableName = modelName; // Assuming modelName is the table name
            StringBuilder sql = new StringBuilder("UPDATE ").append(tableName).append(" SET ");
            List<String> columnsToUpdate = entity.keySet().stream().collect(Collectors.toList());
            sql.append(columnsToUpdate.stream().map(col -> col + " = ?").collect(Collectors.joining(", ")));
            sql.append(" WHERE id = ?"); // Assuming 'id' is the primary key

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
