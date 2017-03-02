package com.a.eye.skywalking.collector.worker.application.persistence;

import com.a.eye.skywalking.collector.actor.AbstractWorkerProvider;

/**
 * @author pengys5
 */
public class ApplicationPersistenceFactory extends AbstractWorkerProvider {
    @Override
    public Class workerClass() {
        return ApplicationPersistence.class;
    }

    @Override
    public int workerNum() {
        return 0;
    }
}
