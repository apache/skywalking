package com.a.eye.skywalking.collector.worker.application.persistence;

import com.a.eye.skywalking.collector.actor.AbstractWorkerProvider;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.PersistenceWorker;
import com.google.gson.JsonObject;

/**
 * @author pengys5
 */
public class DAGNodePersistence extends PersistenceWorker<DAGNodePersistence.Metric> {

    @Override
    public String esIndex() {
        return "application";
    }

    @Override
    public String esType() {
        return "dag_node";
    }

    @Override
    public void analyse(Object message) throws Throwable {
        if (message instanceof Metric) {
            Metric metric = (Metric) message;
            JsonObject propertyJsonObj = new JsonObject();
            propertyJsonObj.addProperty("code", metric.code);
            propertyJsonObj.addProperty("component", metric.component);
            propertyJsonObj.addProperty("layer", metric.layer);

            putData(metric.code, propertyJsonObj);
        }
    }

    public static class Factory extends AbstractWorkerProvider {
        @Override
        public Class workerClass() {
            return DAGNodePersistence.class;
        }

        @Override
        public int workerNum() {
            return WorkerConfig.WorkerNum.DAGNodePersistence_Num;
        }
    }

    public static class Metric {
        private final String code;
        private final String component;
        private final String layer;

        public Metric(String code, String component, String layer) {
            this.code = code;
            this.component = component;
            this.layer = layer;
        }
    }
}
