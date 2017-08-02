package org.skywalking.apm.collector.agentstream.worker.storage;

import java.util.List;
import org.skywalking.apm.collector.core.framework.Starter;
import org.skywalking.apm.collector.stream.worker.WorkerException;
import org.skywalking.apm.collector.stream.worker.impl.ExchangeWorker;
import org.skywalking.apm.collector.stream.worker.impl.ExchangeWorkerContainer;
import org.skywalking.apm.collector.stream.worker.impl.FlushAndSwitch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class IDNameExchangeTimer implements Starter {

    private final Logger logger = LoggerFactory.getLogger(IDNameExchangeTimer.class);

    public void start() {
        logger.info("id and name exchange timer start");
        //TODO timer value config
//        final long timeInterval = EsConfig.Es.Persistence.Timer.VALUE * 1000;
        final long timeInterval = 3 * 1000;

        Thread exchangeThread = new Thread(() -> {
            while (true) {
                try {
                    exchangeLastData();
                    Thread.sleep(timeInterval);
                } catch (Throwable e) {
                    logger.error(e.getMessage(), e);
                }
            }
        });
        exchangeThread.setName("timerExchange");
        exchangeThread.start();
    }

    private void exchangeLastData() {
        List<ExchangeWorker> workers = ExchangeWorkerContainer.INSTANCE.getExchangeWorkers();
        workers.forEach((ExchangeWorker worker) -> {
            try {
                worker.allocateJob(new FlushAndSwitch());
                worker.exchangeLastData();
            } catch (WorkerException e) {
                logger.error(e.getMessage(), e);
            }
        });
    }
}
