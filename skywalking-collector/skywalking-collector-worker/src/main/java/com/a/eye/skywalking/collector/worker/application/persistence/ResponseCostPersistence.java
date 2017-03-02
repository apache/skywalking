package com.a.eye.skywalking.collector.worker.application.persistence;

import com.a.eye.skywalking.collector.actor.AbstractWorkerProvider;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.PersistenceWorker;
import com.google.gson.JsonObject;

/**
 * @author pengys5
 */
public class ResponseCostPersistence extends PersistenceWorker<ResponseCostPersistence.Metric> {

    @Override
    public String esIndex() {
        return "application_metric";
    }

    @Override
    public String esType() {
        return "response_cost";
    }

    @Override
    public void analyse(Object message) throws Throwable {
        if (message instanceof Metric) {
            Metric metric = (Metric) message;
            long cost = metric.startTime - metric.endTime;
            JsonObject data;
            if (containsId(metric.code)) {
                data = getData(metric.code);
            } else {
                data = new JsonObject();
            }

            String propertyKey = "";

            if (cost <= 1000 && !metric.isError) {
                propertyKey = "one_second_less";
            } else if (cost > 1000 && cost <= 3000 && !metric.isError) {
                propertyKey = "three_second_less";
            } else if (cost > 3000 && cost <= 5000 && !metric.isError) {
                propertyKey = "five_second_less";
            } else if (cost > 5000 && cost <= 5000 && !metric.isError) {
                propertyKey = "slow";
            } else {
                propertyKey = "error";
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
            return ResponseCostPersistence.class;
        }

        @Override
        public int workerNum() {
            return WorkerConfig.WorkerNum.ResponseCostPersistence_Num;
        }
    }

    public static class Metric {
        private final String code;
        private final Boolean isError;
        private final Long startTime;
        private final Long endTime;

        public Metric(String code, Boolean isError, Long startTime, Long endTime) {
            this.code = code;
            this.isError = isError;
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }
}
