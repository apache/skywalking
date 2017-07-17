package org.skywalking.apm.collector.core.worker;

/**
 * @author pengys5
 */
public class LocalWorkerContext extends WorkerContext {

    @Override
    final public AbstractWorkerProvider findProvider(Role role) throws ProviderNotFoundException {
        return null;
    }

    @Override
    final public void putProvider(AbstractWorkerProvider provider) throws UsedRoleNameException {

    }
}
