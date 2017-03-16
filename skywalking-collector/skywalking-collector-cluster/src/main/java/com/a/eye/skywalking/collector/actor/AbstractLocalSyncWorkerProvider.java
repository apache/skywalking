package com.a.eye.skywalking.collector.actor;

import java.lang.reflect.Constructor;

/**
 * @author pengys5
 */
public abstract class AbstractLocalSyncWorkerProvider<T extends AbstractLocalSyncWorker> extends AbstractLocalWorkerProvider<T> {

    @Override
    final public WorkerRef create(ClusterWorkerContext clusterContext, LocalWorkerContext localContext) throws Exception {
        validate();

        Constructor workerConstructor = workerClass().getDeclaredConstructor(new Class<?>[]{Role.class, ClusterWorkerContext.class});
        workerConstructor.setAccessible(true);
        T localSyncWorker = (T) workerConstructor.newInstance(role(), clusterContext);
        localSyncWorker.preStart();

        LocalSyncWorkerRef workerRef = new LocalSyncWorkerRef(role(), localSyncWorker);
        localContext.put(workerRef);
        return workerRef;
    }
}
