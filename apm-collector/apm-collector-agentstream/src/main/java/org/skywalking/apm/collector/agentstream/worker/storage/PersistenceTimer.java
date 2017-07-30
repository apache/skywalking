package org.skywalking.apm.collector.agentstream.worker.storage;

import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.collector.core.framework.Starter;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.storage.dao.IBatchDAO;
import org.skywalking.apm.collector.stream.worker.WorkerException;
import org.skywalking.apm.collector.stream.worker.impl.FlushAndSwitch;
import org.skywalking.apm.collector.stream.worker.impl.PersistenceWorker;
import org.skywalking.apm.collector.stream.worker.impl.PersistenceWorkerContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class PersistenceTimer implements Starter {

    private final Logger logger = LoggerFactory.getLogger(PersistenceTimer.class);

    public void start() {
        logger.info("persistence timer start");
        //TODO timer value config
//        final long timeInterval = EsConfig.Es.Persistence.Timer.VALUE * 1000;
        final long timeInterval = 3 * 1000;

        Thread persistenceThread = new Thread(() -> {
            while (true) {
                try {
                    extractDataAndSave();
                    Thread.sleep(timeInterval);
                } catch (Throwable e) {
                    logger.error(e.getMessage(), e);
                }
            }
        });
        persistenceThread.setName("timerPersistence");
        persistenceThread.start();
    }

    private void extractDataAndSave() {
        List<PersistenceWorker> workers = PersistenceWorkerContainer.INSTANCE.getPersistenceWorkers();
        List batchAllCollection = new ArrayList<>();
        workers.forEach((PersistenceWorker worker) -> {
            try {
                worker.allocateJob(new FlushAndSwitch());
                List<?> batchCollection = worker.buildBatchCollection();
                batchAllCollection.addAll(batchCollection);
            } catch (WorkerException e) {
                logger.error(e.getMessage(), e);
            }
        });

        IBatchDAO dao = (IBatchDAO)DAOContainer.INSTANCE.get(IBatchDAO.class.getName());
        dao.batchPersistence(batchAllCollection);
    }
}
