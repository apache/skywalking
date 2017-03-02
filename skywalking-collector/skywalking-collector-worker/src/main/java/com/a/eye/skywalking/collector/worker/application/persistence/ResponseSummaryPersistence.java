package com.a.eye.skywalking.collector.worker.application.persistence;

import com.a.eye.skywalking.collector.actor.AbstractWorkerProvider;
import com.a.eye.skywalking.collector.worker.PersistenceWorker;
import com.google.gson.JsonObject;

import static com.a.eye.skywalking.collector.worker.WorkerConfig.WorkerNum.ResponseSummaryPersistence_Num;

/**
 * @author pengys5
 */
public class ResponseSummaryPersistence extends PersistenceWorker<ResponseSummaryPersistence.Metric> {

    @Override
    public String esIndex() {
        return "application_metric";
    }

    @Override
    public String esType() {
        return "response_summary";
    }

    @Override
    public void analyse(Object message) throws Throwable {
        if (message instanceof Metric) {
            Metric metric = (Metric) message;

            JsonObject data;
            if (containsId(metric.code)) {
                data = getData(metric.code);
            } else {
                data = new JsonObject();
            }

            String propertyKey = "";
            if (metric.isError) {
                propertyKey = "error";
            } else {
                propertyKey = "success";
            }

            if (data.has(propertyKey)) {
                data.addProperty(propertyKey, data.get(propertyKey).getAsLong() + 1);
            } else {
                data.addProperty(propertyKey, 1);
            }
        }
    }

    public static class Factory extends AbstractWorkerProvider {
        @Override
        public Class workerClass() {
            return ResponseSummaryPersistence.class;
        }

        @Override
        public int workerNum() {
            return ResponseSummaryPersistence_Num;
        }
    }

    public static class Metric {
        private final String code;
        private final Boolean isError;

        public Metric(String code, Boolean isError) {
            this.code = code;
            this.isError = isError;
        }
    }
}
