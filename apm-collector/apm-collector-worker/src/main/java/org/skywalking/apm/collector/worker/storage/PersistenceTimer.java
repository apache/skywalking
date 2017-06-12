package org.skywalking.apm.collector.worker.storage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.StringFormattedMessage;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.skywalking.apm.collector.actor.AbstractLocalSyncWorker;
import org.skywalking.apm.collector.worker.config.EsConfig;

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
                } catch (Throwable e) {
                    logger.error(e, e);
                }
            }
        };
        Thread persistenceThread = new Thread(runnable);
        persistenceThread.setName("timerPersistence");
        persistenceThread.start();
    }

    private void extractDataAndSave() {
        List<IndexRequestBuilder> dataList = new LinkedList<>();

        List<AbstractLocalSyncWorker> workers = PersistenceWorkerListener.INSTANCE.getWorkers();
        for (AbstractLocalSyncWorker worker : workers) {
            logger.info("worker role name: %s", worker.getRole().roleName());
            try {
                worker.allocateJob(new FlushAndSwitch(), dataList);
            } catch (Exception e) {
                logger.error(new StringFormattedMessage("flush persistence worker data error, worker role name: %s", worker.getRole().roleName()), e);
            }
        }
        EsClient.INSTANCE.bulk(dataList);
    }
}
