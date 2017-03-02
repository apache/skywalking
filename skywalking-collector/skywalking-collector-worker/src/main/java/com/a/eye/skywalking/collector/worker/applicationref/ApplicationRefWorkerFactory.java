package com.a.eye.skywalking.collector.worker.applicationref;

import com.a.eye.skywalking.collector.actor.AbstractWorkerProvider;

/**
 * @author pengys5
 */
public class ApplicationRefWorkerFactory extends AbstractWorkerProvider {
    @Override
    public Class workerClass() {
        return ApplicationRefWorker.class;
    }

    @Override
    public int workerNum() {
        return 0;
    }
}
