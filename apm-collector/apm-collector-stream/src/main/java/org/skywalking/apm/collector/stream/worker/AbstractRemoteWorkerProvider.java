package org.skywalking.apm.collector.stream.worker;

/**
 * The <code>AbstractRemoteWorkerProvider</code> implementations represent providers,
 * which create instance of cluster workers whose implemented {@link AbstractRemoteWorker}.
 * <p>
 *
 * @author pengys5
 * @since v3.0-2017
 */
public abstract class AbstractRemoteWorkerProvider<T extends AbstractRemoteWorker> extends AbstractWorkerProvider<T> {

    /**
     * Create how many worker instance of {@link AbstractRemoteWorker} in one jvm.
     *
     * @return The worker instance number.
     */
    public abstract int workerNum();

    /**
     * Create the worker instance into akka system, the akka system will control the cluster worker life cycle.
     *
     * @return The created worker reference. See {@link RemoteWorkerRef}
     * @throws ProviderNotFoundException This worker instance attempted to find a provider which use to create another
     * worker instance, when the worker provider not find then Throw this Exception.
     */
    @Override final public WorkerRef create() throws ProviderNotFoundException {
        T clusterWorker = workerInstance(getClusterContext());
        clusterWorker.preStart();

        RemoteWorkerRef workerRef = new RemoteWorkerRef(role(), clusterWorker);
        getClusterContext().put(workerRef);
        return workerRef;
    }
}
