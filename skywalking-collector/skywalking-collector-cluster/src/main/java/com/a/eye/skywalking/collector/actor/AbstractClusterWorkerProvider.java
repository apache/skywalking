package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * @author pengys5
 */
public abstract class AbstractClusterWorkerProvider<T extends AbstractClusterWorker> extends AbstractWorkerProvider<T> {

    public abstract int workerNum();

    @Override
    final public WorkerRef onCreate(ClusterWorkerContext clusterContext, LocalWorkerContext localContext) throws IllegalArgumentException, ProviderNotFountException {
        int num = ClusterWorkerRefCounter.INSTANCE.incrementAndGet(role());

        T clusterWorker = (T) workerInstance(clusterContext);
        clusterWorker.preStart();

        ActorRef actorRef = clusterContext.getAkkaSystem().actorOf(Props.create(AbstractClusterWorker.WorkerWithAkka.class, clusterWorker), role() + "_" + num);

        ClusterWorkerRef workerRef = new ClusterWorkerRef(actorRef, role());
        clusterContext.put(workerRef);
        return workerRef;
    }
}
