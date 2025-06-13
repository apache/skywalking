package org.apache.skywalking.oap.server.storage.plugin.doris.dao;

import java.io.IOException;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.storage.IRecordDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.storage.plugin.doris.client.DorisClient;
import org.apache.skywalking.oap.server.storage.plugin.doris.util.DorisDAOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DorisRecordDAO implements IRecordDAO {

    private static final Logger LOGGER = LoggerFactory.getLogger(DorisRecordDAO.class);
    private final DorisClient dorisClient;

    public DorisRecordDAO(DorisClient dorisClient) {
        this.dorisClient = dorisClient;
    }

    /**
     * Implements the IRecordDAO interface method.
     * Delegates to the public method with String modelName.
     */
    @Override
    public InsertRequest prepareBatchInsert(Model model, Record record) throws IOException {
        LOGGER.debug("IRecordDAO.prepareBatchInsert(Model, Record) called, delegating to (String, Record) version. Model: {}", model.getName());
        return this.prepareBatchInsert(model.getName(), record);
    }

    /**
     * Prepares an InsertRequest for a given record, as specified by the subtask prompt.
     *
     * @param modelName The name of the model, used as the table name.
     * @param record    The Record object (which is a Map<String, Object>) to be inserted.
     * @return An InsertRequest ready for execution.
     * @throws IOException If the record is null or empty.
     */
    public InsertRequest prepareBatchInsert(String modelName, Record record) throws IOException {
        if (record == null || record.isEmpty()) {
            // As per subtask, Record is a Map. An empty map means no columns to insert.
            LOGGER.warn("Attempting to insert an empty or null record into table: {}", modelName);
            // Throwing an exception might be more appropriate than returning a no-op request,
            // as it indicates a problem with the data pipeline.
            throw new IOException("Cannot insert null or empty record into table " + modelName);
        }

        // The Record object itself is already a Map<String, Object>.
        // The keys are column names, and values are the corresponding values.
        // The modelName provides the table name.
        // StorageBuilder is not used by DorisDAOUtils.prepareInsertRequest currently, so passing null.
        LOGGER.debug("Preparing batch insert for table: {}, record content size: {}", modelName, record.size());
        return DorisDAOUtils.prepareInsertRequest(dorisClient, modelName, record, null);
    }
}
