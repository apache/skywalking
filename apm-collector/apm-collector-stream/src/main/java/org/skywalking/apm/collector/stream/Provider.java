package org.skywalking.apm.collector.stream;

/**
 * @author pengys5
 */
public interface Provider {

    WorkerRef create(AbstractWorker workerOwner) throws ProviderNotFoundException;
}
