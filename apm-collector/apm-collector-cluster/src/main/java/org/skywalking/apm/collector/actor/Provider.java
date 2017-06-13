package org.skywalking.apm.collector.actor;

/**
 * @author pengys5
 */
public interface Provider {

    WorkerRef create(AbstractWorker workerOwner) throws ProviderNotFoundException;
}
