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
public class TraceSegmentRecordPersistence extends PersistenceWorker {

    private Logger logger = LogManager.getFormatterLogger(TraceSegmentRecordPersistence.class);

    @Override
    public String esIndex() {
        return "application_record";
    }

    @Override
    public String esType() {
        return "trace_segment";
    }

    @Override
    public void analyse(Object message) throws Throwable {
        if (message instanceof JsonObject) {
            JsonObject traceSegmentJsonObj = (JsonObject) message;
            logger.debug("segmentId: %s, json record: %s", traceSegmentJsonObj.get("segmentId").getAsString(), traceSegmentJsonObj.toString());
            putData(traceSegmentJsonObj.get("segmentId").getAsString(), traceSegmentJsonObj);
        } else {
            logger.error("message unhandled");
        }
    }

    public static class Factory extends AbstractWorkerProvider {
        public static Factory INSTANCE = new Factory();
        @Override
        public Class workerClass() {
            return TraceSegmentRecordPersistence.class;
        }

        @Override
        public int workerNum() {
            return WorkerConfig.Worker.TraceSegmentRecordPersistence.Num;
        }
    }
}
