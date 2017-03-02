package com.a.eye.skywalking.collector.worker.persistence;

import com.a.eye.skywalking.collector.actor.AbstractWorkerProvider;

/**
 * @author pengys5
 */
public class AppTraceSegmentRecordFactory extends AbstractWorkerProvider {
    @Override
    public Class workerClass() {
        return AppTraceSegmentRecord.class;
    }

    @Override
    public int workerNum() {
        return 0;
    }
}
