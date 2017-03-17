package com.a.eye.skywalking.collector.actor;

/**
 * @author pengys5
 */
public class LocalWorkerContext extends WorkerContext {

    @Override
    final public AbstractWorkerProvider findProvider(Role role) throws ProviderNotFountException {
        return null;
    }

    @Override
    final public void putProvider(AbstractWorkerProvider provider) throws UsedRoleNameException {

    }
}
