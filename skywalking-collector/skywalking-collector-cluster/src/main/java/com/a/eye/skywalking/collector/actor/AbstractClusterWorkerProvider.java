package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * The <code>AbstractClusterWorkerProvider</code> implementations represent providers,
 * which create instance of cluster workers whose implemented {@link AbstractClusterWorker}.
 * <p>
 *
 * @author pengys5
 * @since v3.0-2017
 */
public abstract class AbstractClusterWorkerProvider<T extends AbstractClusterWorker> extends AbstractWorkerProvider<T> {

    /**
     * Create how many worker instance of {@link AbstractClusterWorker} in one jvm.
     *
     * @return The worker instance number.
     */
    public abstract int workerNum();

    /**
     * Create the worker instance into akka system, the akka system will control the cluster worker life cycle.
     *
     * @param localContext Not used, will be null.
     * @return The created worker reference. See {@link ClusterWorkerRef}
     * @throws IllegalArgumentException Not used.
     * @throws ProviderNotFoundException This worker instance attempted to find a provider which use to create another
     * worker instance, when the worker provider not find then Throw this Exception.
     */
    @Override final public WorkerRef onCreate(
        LocalWorkerContext localContext) throws IllegalArgumentException, ProviderNotFoundException {
        int num = ClusterWorkerRefCounter.INSTANCE.incrementAndGet(role());

        T clusterWorker = workerInstance(getClusterContext());
        clusterWorker.preStart();

        ActorRef actorRef = getClusterContext().getAkkaSystem().actorOf(Props.create(AbstractClusterWorker.WorkerWithAkka.class, clusterWorker), role().roleName() + "_" + num);

        ClusterWorkerRef workerRef = new ClusterWorkerRef(actorRef, role());
        getClusterContext().put(workerRef);
        return workerRef;
    }
}
