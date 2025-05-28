package org.apache.skywalking.oap.server.storage.plugin.doris.dao;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.skywalking.oap.server.core.storage.IBatchDAO;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;
import org.apache.skywalking.oap.server.storage.plugin.doris.StorageModuleDorisConfig;
import org.apache.skywalking.oap.server.storage.plugin.doris.client.DorisClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DorisBatchDAO implements IBatchDAO {

    private static final Logger LOGGER = LoggerFactory.getLogger(DorisBatchDAO.class);
    private final DorisClient dorisClient;
    // private final int bulkActions; // Example: if we use config for batch size for internal buffering
    // private final int flushInterval; // Example: if we implement timed flushing internally

    public DorisBatchDAO(DorisClient dorisClient, StorageModuleDorisConfig config) {
        this.dorisClient = dorisClient;
        // this.bulkActions = config.getBulkActions(); // If such config existed
        // this.flushInterval = config.getFlushInterval(); // If such config existed
        LOGGER.info("DorisBatchDAO created. Note: True JDBC batching (addBatch/executeBatch) is not yet implemented. Requests are executed individually.");
    }

    /**
     * Executes a single insert request directly.
     * For batching, requests should ideally be collected and processed by flush().
     * @param insertRequest data to insert.
     */
    @Override
    public void insert(InsertRequest insertRequest) {
        if (insertRequest == null) {
            LOGGER.warn("Null InsertRequest received in insert(). Skipping.");
            return;
        }
        try {
            LOGGER.debug("Executing single insert request via IBatchDAO.insert()");
            insertRequest.execute();
        } catch (IOException e) {
            LOGGER.error("Failed to execute single insert request: {}", e.getMessage(), e);
            // Depending on SkyWalking's error handling for IBatchDAO.insert,
            // we might need to rethrow or handle differently.
            // For now, logging the error.
        }
    }

    /**
     * Executes a list of {@link PrepareRequest} objects.
     * Currently, this iterates and executes each request individually.
     * True JDBC batching (addBatch/executeBatch) is a future enhancement.
     *
     * @param prepareRequests List of requests to execute.
     * @return CompletableFuture that completes when all requests are processed.
     */
    @Override
    public CompletableFuture<Void> flush(List<PrepareRequest> prepareRequests) {
        if (prepareRequests == null || prepareRequests.isEmpty()) {
            LOGGER.debug("No requests to flush.");
            return CompletableFuture.completedFuture(null);
        }

        LOGGER.debug("Flushing {} requests. Executing individually.", prepareRequests.size());
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            prepareRequests.stream()
                .map(request -> CompletableFuture.runAsync(() -> {
                    try {
                        request.execute();
                    } catch (IOException e) {
                        // Encapsulate checked IOException in an unchecked exception for CompletableFuture
                        LOGGER.error("Failed to execute request during flush: {}", e.getMessage(), e);
                        throw new RuntimeException("Failed to execute request: " + request, e);
                    }
                }))
                .toArray(CompletableFuture[]::new)
        );

        return allFutures.whenComplete((result, throwable) -> {
            if (throwable != null) {
                LOGGER.error("Error during flush execution of batch requests.", throwable);
            } else {
                LOGGER.debug("Successfully flushed {} requests.", prepareRequests.size());
            }
        });
    }

    /**
     * Default implementation from IBatchDAO, can be overridden if specific cleanup is needed.
     */
    @Override
    public void endOfFlush() {
        LOGGER.debug("endOfFlush() called on DorisBatchDAO.");
        // Perform any cleanup or finalization if necessary for a batch period.
    }
}
