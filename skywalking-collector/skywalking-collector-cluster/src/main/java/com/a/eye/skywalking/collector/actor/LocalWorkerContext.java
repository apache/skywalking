package com.a.eye.skywalking.collector.actor;

import java.util.List;
import java.util.Map;

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
