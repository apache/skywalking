package com.a.eye.skywalking.collector.worker.metric;

import com.a.eye.skywalking.collector.actor.AbstractWorkerProvider;
import com.a.eye.skywalking.collector.cluster.config.CollectorConfig;

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
        return CollectorConfig.Collector.Worker.ApplicationDiscoverMetric_Num;
    }
}
