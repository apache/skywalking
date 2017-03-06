package com.a.eye.skywalking.collector.worker.application.persistence;

import com.a.eye.skywalking.collector.actor.AbstractWorkerProvider;
import com.a.eye.skywalking.collector.worker.PersistenceWorker;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Serializable;

/**
 * @author pengys5
 */
public class ResponseSummaryPersistence extends PersistenceWorker<ResponseSummaryPersistence.Metric> {

    private Logger logger = LogManager.getFormatterLogger(ResponseSummaryPersistence.class);

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

            String propertyKey;
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
            logger.debug("response summary metric: %s", data.toString());
        }
    }

    public static class Factory extends AbstractWorkerProvider {
        public static Factory INSTANCE = new Factory();

        @Override
        public Class workerClass() {
            return ResponseSummaryPersistence.class;
        }

        @Override
        public int workerNum() {
            return WorkerConfig.Worker.ResponseSummaryPersistence.Num;
        }
    }

    public static class Metric implements Serializable {
        private final String code;
        private final Boolean isError;

        public Metric(String code, Boolean isError) {
            this.code = code;
            this.isError = isError;
        }
    }
}
