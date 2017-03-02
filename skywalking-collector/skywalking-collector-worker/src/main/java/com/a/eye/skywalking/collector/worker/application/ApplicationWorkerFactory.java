package com.a.eye.skywalking.collector.worker.application;

import com.a.eye.skywalking.collector.actor.AbstractWorkerProvider;

/**
 * @author pengys5
 */
public class ApplicationWorkerFactory extends AbstractWorkerProvider {
    @Override
    public Class workerClass() {
        return ApplicationWorker.class;
    }

    @Override
    public int workerNum() {
        return 0;
    }
}
