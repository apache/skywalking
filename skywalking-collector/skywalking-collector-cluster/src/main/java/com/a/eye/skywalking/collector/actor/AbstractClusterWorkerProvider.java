package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * @author pengys5
 */
public abstract class AbstractClusterWorkerProvider<T extends AbstractClusterWorker> extends AbstractWorkerProvider<T> {

    public abstract int workerNum();

    @Override
    final public WorkerRef onCreate(LocalWorkerContext localContext) throws IllegalArgumentException, ProviderNotFoundException {
        int num = ClusterWorkerRefCounter.INSTANCE.incrementAndGet(role());

        T clusterWorker = (T) workerInstance(getClusterContext());
        clusterWorker.preStart();

        ActorRef actorRef = getClusterContext().getAkkaSystem().actorOf(Props.create(AbstractClusterWorker.WorkerWithAkka.class, clusterWorker), role().roleName() + "_" + num);

        ClusterWorkerRef workerRef = new ClusterWorkerRef(actorRef, role());
        getClusterContext().put(workerRef);
        return workerRef;
    }
}
