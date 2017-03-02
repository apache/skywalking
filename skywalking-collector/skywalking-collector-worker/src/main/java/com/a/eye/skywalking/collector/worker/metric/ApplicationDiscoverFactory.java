package com.a.eye.skywalking.collector.worker.metric;

import com.a.eye.skywalking.collector.actor.AbstractWorkerProvider;

/**
 * @author pengys5
 */
public class ApplicationDiscoverFactory extends AbstractWorkerProvider {

    @Override
    public Class workerClass() {
        return ApplicationDiscoverMetric.class;
    }

    @Override
    public int workerNum() {
        return 0;
    }
}
