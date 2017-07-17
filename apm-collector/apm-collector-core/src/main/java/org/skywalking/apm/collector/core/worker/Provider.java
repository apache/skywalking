package org.skywalking.apm.collector.core.worker;

/**
 * @author pengys5
 */
public interface Provider {

    WorkerRef create(AbstractWorker workerOwner) throws ProviderNotFoundException;
}
