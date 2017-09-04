package org.skywalking.apm.collector.stream.worker;

/**
 * @author pengys5
 */
public interface LookUp {

    WorkerRefs lookup(Role role) throws WorkerNotFoundException;
}
