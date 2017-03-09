package com.a.eye.skywalking.collector.worker.application.receiver;

import com.a.eye.skywalking.collector.actor.AbstractWorker;
import com.a.eye.skywalking.collector.actor.AbstractWorkerProvider;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.application.persistence.ResponseCostPersistence;
import com.a.eye.skywalking.collector.worker.storage.MetricPersistenceData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class ResponseCostReceiver extends AbstractWorker {

    private Logger logger = LogManager.getFormatterLogger(ResponseCostReceiver.class);

    private ResponseCostPersistence persistence;

    @Override
    public void preStart() throws Exception {
        super.preStart();
        persistence = ResponseCostPersistence.Factory.INSTANCE.createWorker(getSelf());
    }

    @Override
    public void receive(Object message) throws Throwable {
        if (message instanceof MetricPersistenceData) {
            persistence.beTold(message);
        } else {
            logger.error("message unhandled");
        }
    }

    public static class Factory extends AbstractWorkerProvider {
        public static Factory INSTANCE = new Factory();

        @Override
        public Class workerClass() {
            return ResponseCostReceiver.class;
        }

        @Override
        public int workerNum() {
            return WorkerConfig.Worker.ResponseCostReceiver.Num;
        }
    }
}
