package com.a.eye.skywalking.collector.worker.application.receiver;

import com.a.eye.skywalking.collector.actor.AbstractWorker;
import com.a.eye.skywalking.collector.actor.AbstractWorkerProvider;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.application.persistence.DAGNodePersistence;
import com.a.eye.skywalking.collector.worker.storage.RecordData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class DAGNodeReceiver extends AbstractWorker {

    private Logger logger = LogManager.getFormatterLogger(DAGNodeReceiver.class);

    private DAGNodePersistence persistence;

    @Override
    public void preStart() throws Exception {
        super.preStart();
        persistence = DAGNodePersistence.Factory.INSTANCE.createWorker(getSelf());
    }

    @Override
    public void receive(Object message) throws Throwable {
        if (message instanceof RecordData) {
            persistence.beTold(message);
        } else {
            logger.error("message unhandled");
        }
    }

    public static class Factory extends AbstractWorkerProvider {
        public static Factory INSTANCE = new Factory();

        @Override
        public Class workerClass() {
            return DAGNodeReceiver.class;
        }

        @Override
        public int workerNum() {
            return WorkerConfig.Worker.DAGNodeReceiver.Num;
        }
    }
}
