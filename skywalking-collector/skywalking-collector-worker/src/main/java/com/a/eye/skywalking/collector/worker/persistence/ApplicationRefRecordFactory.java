package com.a.eye.skywalking.collector.worker.persistence;

import com.a.eye.skywalking.collector.actor.AbstractWorkerProvider;

/**
 * @author pengys5
 */
public class ApplicationRefRecordFactory extends AbstractWorkerProvider {
    @Override
    public Class workerClass() {
        return ApplicationRefRecord.class;
    }

    @Override
    public int workerNum() {
        return 0;
    }
}
