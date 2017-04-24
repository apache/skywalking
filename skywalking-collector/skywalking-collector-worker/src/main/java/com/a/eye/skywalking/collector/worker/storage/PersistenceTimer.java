package com.a.eye.skywalking.collector.worker.storage;

import com.a.eye.skywalking.collector.actor.AbstractLocalSyncWorker;
import com.a.eye.skywalking.collector.worker.config.EsConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.index.IndexRequestBuilder;

import java.util.LinkedList;
import java.util.List;

/**
 * @author pengys5
 */
public enum PersistenceTimer {
    INSTANCE;

    private Logger logger = LogManager.getFormatterLogger(PersistenceTimer.class);

    public void boot() {
        logger.info("persistence timer start");
        final long timeInterval = EsConfig.Es.Persistence.Timer.VALUE * 1000;

        Runnable runnable = () -> {
            while (true) {
                try {
                    extractDataAndSave();
                    Thread.sleep(timeInterval);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        Thread thread = new Thread(runnable);
        thread.start();
    }

    private void extractDataAndSave() {
        List<IndexRequestBuilder> dataList = new LinkedList<>();

        List<AbstractLocalSyncWorker> workers = PersistenceWorkerListener.INSTANCE.getWorkers();
        for (AbstractLocalSyncWorker worker : workers) {
            logger.info("worker role name: %s", worker.getRole().roleName());
            try {
                worker.allocateJob(new FlushAndSwitch(), dataList);
            } catch (Exception e) {
                logger.error("flush persistence worker data error, worker role name: %s", worker.getRole().roleName());
                e.printStackTrace();
            }
        }
        EsClient.INSTANCE.bulk(dataList);
    }
}
