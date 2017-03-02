package com.a.eye.skywalking.collector.worker.persistence;

import com.a.eye.skywalking.collector.actor.AbstractWorkerProvider;

/**
 * @author pengys5
 */
public class AppResponseSummaryFactory extends AbstractWorkerProvider {
    @Override
    public Class workerClass() {
        return AppResponseSummary.class;
    }

    @Override
    public int workerNum() {
        return 0;
    }
}
