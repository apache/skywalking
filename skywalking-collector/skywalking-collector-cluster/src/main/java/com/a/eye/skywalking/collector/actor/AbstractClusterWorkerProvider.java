package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorRef;
import akka.actor.Props;

import java.lang.reflect.Constructor;

/**
 * @author pengys5
 */
public abstract class AbstractClusterWorkerProvider<T extends AbstractClusterWorker> extends AbstractWorkerProvider<T> {

    public abstract int workerNum();

    @Override
    final public WorkerRef create(ClusterWorkerContext clusterContext, LocalWorkerContext localContext) throws Exception {
        int num = ClusterWorkerRefCounter.INSTANCE.incrementAndGet(role());

        Constructor workerConstructor = workerClass().getDeclaredConstructor(new Class<?>[]{Role.class, ClusterWorkerContext.class});
        workerConstructor.setAccessible(true);
        T clusterWorker = (T) workerConstructor.newInstance(role(), clusterContext);
        clusterWorker.preStart();

        ActorRef actorRef = clusterContext.getAkkaSystem().actorOf(Props.create(AbstractClusterWorker.WorkerWithAkka.class, clusterWorker), role() + "_" + num);

        ClusterWorkerRef workerRef = new ClusterWorkerRef(actorRef, role());
        clusterContext.put(workerRef);
        return workerRef;
    }
}
