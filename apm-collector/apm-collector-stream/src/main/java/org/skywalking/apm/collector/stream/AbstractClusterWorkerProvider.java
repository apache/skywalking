package org.skywalking.apm.collector.stream;

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
     * @throws ProviderNotFoundException This worker instance attempted to find a provider which use to create another
     * worker instance, when the worker provider not find then Throw this Exception.
     */
    @Override final public WorkerRef onCreate(
        LocalWorkerContext localContext) throws ProviderNotFoundException {
        T clusterWorker = workerInstance(getClusterContext());
        clusterWorker.preStart();

        ClusterWorkerRef workerRef = new ClusterWorkerRef(role(), clusterWorker);
        getClusterContext().put(workerRef);
        return workerRef;
    }
}
