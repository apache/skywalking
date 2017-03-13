package com.a.eye.skywalking.collector.worker.application.receiver;

import com.a.eye.skywalking.collector.actor.AbstractWorker;
import com.a.eye.skywalking.collector.actor.AbstractWorkerProvider;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.application.persistence.ResponseSummaryPersistence;
import com.a.eye.skywalking.collector.worker.storage.MetricData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class ResponseSummaryReceiver extends AbstractWorker {

    private Logger logger = LogManager.getFormatterLogger(ResponseSummaryReceiver.class);

    private ResponseSummaryPersistence persistence;

    @Override
    public void preStart() throws Exception {
        super.preStart();
        persistence = ResponseSummaryPersistence.Factory.INSTANCE.createWorker(getSelf());
    }

    @Override
    public void receive(Object message) throws Throwable {
        if (message instanceof MetricData) {
            persistence.beTold(message);
        } else {
            logger.error("message unhandled");
        }
    }

    public static class Factory extends AbstractWorkerProvider {
        public static Factory INSTANCE = new Factory();

        @Override
        public Class workerClass() {
            return ResponseSummaryReceiver.class;
        }

        @Override
        public int workerNum() {
            return WorkerConfig.Worker.ResponseSummaryReceiver.Num;
        }
    }
}
