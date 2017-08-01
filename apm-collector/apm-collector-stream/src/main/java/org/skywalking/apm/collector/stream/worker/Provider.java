package org.skywalking.apm.collector.stream.worker;

/**
 * @author pengys5
 */
public interface Provider {

    WorkerRef create() throws ProviderNotFoundException;
}
