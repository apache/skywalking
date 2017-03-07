package com.a.eye.skywalking.collector.worker.application.persistence;

import com.a.eye.skywalking.collector.actor.AbstractWorkerProvider;
import com.a.eye.skywalking.collector.worker.PersistenceWorker;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public class NodeInstancePersistence extends PersistenceWorker {

    private Logger logger = LogManager.getFormatterLogger(NodeInstancePersistence.class);

    @Override
    public String esIndex() {
        return "application";
    }

    @Override
    public String esType() {
        return "node_instance";
    }

    @Override
    public void analyse(Object message) throws Throwable {
        if (message instanceof Metric) {
            Metric metric = (Metric) message;
            JsonObject propertyJsonObj = new JsonObject();
            propertyJsonObj.addProperty("code", metric.code);
            propertyJsonObj.addProperty("address", metric.address);

            putData(metric.address, propertyJsonObj);
            logger.debug("node instance: %s", propertyJsonObj.toString());
        } else {
            logger.error("message unhandled");
        }
    }

    public static class Factory extends AbstractWorkerProvider {
        public static Factory INSTANCE = new Factory();

        @Override
        public Class workerClass() {
            return NodeInstancePersistence.class;
        }

        @Override
        public int workerNum() {
            return WorkerConfig.Worker.NodeInstancePersistence.Num;
        }
    }

    public static class Metric {
        private final String code;
        private final String address;

        public Metric(String code, String address) {
            this.code = code;
            this.address = address;
        }
    }
}
