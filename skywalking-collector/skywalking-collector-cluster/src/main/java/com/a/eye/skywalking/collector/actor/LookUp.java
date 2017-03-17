package com.a.eye.skywalking.collector.actor;

/**
 * @author pengys5
 */
public interface LookUp {

    WorkerRefs lookup(Role role) throws WorkerNotFoundException;

    Provider findProvider(Role role) throws ProviderNotFoundException;
}
