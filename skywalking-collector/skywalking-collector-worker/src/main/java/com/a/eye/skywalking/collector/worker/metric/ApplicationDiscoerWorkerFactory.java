package com.a.eye.skywalking.collector.worker.metric;

import com.a.eye.skywalking.collector.actor.AbstractWorkerProvider;

/**
 * @author pengys5
 */
public class ApplicationDiscoerWorkerFactory extends AbstractWorkerProvider {

    public static final String WorkerName = "ApplicationDiscoverMetric";

    @Override
    public String workerName() {
        return WorkerName;
    }

    @Override
    public Class workerClass() {
        return ApplicationDiscoverMetric.class;
    }

    @Override
    public int workerNum() {
        return 0;
    }
}
