package com.a.eye.skywalking.collector.worker.application.persistence;

import com.a.eye.skywalking.collector.actor.AbstractWorkerProvider;
import com.a.eye.skywalking.collector.worker.PersistenceWorker;
import com.google.gson.JsonObject;

import static com.a.eye.skywalking.collector.worker.WorkerConfig.WorkerNum.TraceSegmentRecordPersistence_Num;

/**
 * @author pengys5
 */
public class TraceSegmentRecordPersistence extends PersistenceWorker {

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
            putData(traceSegmentJsonObj.get("segmentId").getAsString(), traceSegmentJsonObj);
        }
    }

    public static class Factory extends AbstractWorkerProvider {
        @Override
        public Class workerClass() {
            return TraceSegmentRecordPersistence.class;
        }

        @Override
        public int workerNum() {
            return TraceSegmentRecordPersistence_Num;
        }
    }
}
