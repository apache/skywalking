package com.a.eye.skywalking.collector.worker.applicationref.presistence;

import com.a.eye.skywalking.collector.actor.AbstractWorkerProvider;
import com.a.eye.skywalking.collector.worker.PersistenceWorker;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.google.gson.JsonObject;

import java.io.Serializable;

/**
 * @author pengys5
 */
public class DAGNodeRefPersistence extends PersistenceWorker<DAGNodeRefPersistence.Metric> {

    @Override
    public String esIndex() {
        return "node_ref";
    }

    @Override
    public String esType() {
        return "node_ref";
    }

    @Override
    public void analyse(Object message) throws Throwable {
        if (message instanceof Metric) {
            Metric metric = (Metric) message;
            JsonObject propertyJsonObj = new JsonObject();
            propertyJsonObj.addProperty("frontCode", metric.frontCode);
            propertyJsonObj.addProperty("behindCode", metric.behindCode);

            putData(metric.frontCode + "-" + metric.behindCode, propertyJsonObj);
        }
    }

    public static class Factory extends AbstractWorkerProvider {

        public static Factory INSTANCE = new Factory();

        @Override
        public Class workerClass() {
            return DAGNodeRefPersistence.class;
        }

        @Override
        public int workerNum() {
            return WorkerConfig.Worker.DAGNodeRefPersistence.Num;
        }
    }

    public static class Metric implements Serializable {
        private final String frontCode;
        private final String behindCode;

        public Metric(String frontCode, String behindCode) {
            this.frontCode = frontCode;
            this.behindCode = behindCode;
        }
    }
}
