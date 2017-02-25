package com.a.eye.skywalking.collector.actor;

/**
 * @author pengys5
 */
public class SpiTestWorkerFactory extends AbstractWorkerProvider {

    public static final String WorkerRole = "SpiTestWorker";

    @Override
    public Class workerClass() {
        return SpiTestWorker.class;
    }

    @Override
    public int workerNum() {
        return 2;
    }
}
