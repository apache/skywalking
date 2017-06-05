package org.skywalking.apm.collector.worker.instance.util;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.VersionType;
import org.elasticsearch.index.engine.VersionConflictEngineException;
import org.skywalking.apm.collector.worker.storage.EsClient;

public class ESLock {

    private Logger logger = LogManager.getLogger(ESLock.class);

    public ESLock() {
        Client client = EsClient.INSTANCE.getClient();
        try {
            Map<String, Object> source = new HashMap<>();
            source.put("next_start_value", 0);
            client.prepareIndex("records", "instance-ids", "1").setVersion(0)
                .setVersionType(VersionType.EXTERNAL).setSource(source).get();
        } catch (VersionConflictEngineException e) {
            
        }
    }

    public boolean tryLock(LockListener listener) {
        try {
            Result searchResult = searchIndex();
            if (updateSuccess(searchResult)) {
                listener.lockSuccess(searchResult.currentInstance, searchResult.getNextStartCount());
                return true;
            }
        } catch (Exception e) {
            logger.error("Lock failure.", e);
        }

        return false;
    }

    private boolean updateSuccess(Result result) throws InterruptedException {
        Map<String, Object> source = new HashMap<String, Object>();
        source.put("next_start_value", result.getNextStartCount());
        IndexRequest updateRequest = new IndexRequest();
        updateRequest.index("records");
        updateRequest.type("instance-ids");
        updateRequest.id("1");
        updateRequest.version(result.getNextVersion());
        updateRequest.versionType(VersionType.EXTERNAL);
        updateRequest.source(source);
        try {
            EsClient.INSTANCE.getClient().index(updateRequest).get();
        } catch (ExecutionException e) {
            return false;
        }
        return true;
    }

    public interface LockListener {
        void lockSuccess(long start, long end);
    }

    private Result searchIndex() {
        GetResponse response = EsClient.INSTANCE.getClient().prepareGet("records", "instance-ids", "1").get();
        return new Result(response);
    }

    private class Result {
        private long version;
        private long currentInstance;
        private static final int STEP = 20;

        public Result(GetResponse response) {
            this.version = response.getVersion();
            this.currentInstance = Integer.valueOf(response.getSource().get("next_start_value").toString());
        }

        public long getNextStartCount() {
            return currentInstance + STEP;
        }

        public long getNextVersion() {
            return version + 1;
        }
    }
}
