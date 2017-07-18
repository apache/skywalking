package org.skywalking.apm.collector.stream;

/**
 * The <code>AbstractLocalAsyncWorker</code> implementations represent workers,
 * which receive local asynchronous message.
 *
 * @author pengys5
 * @since v3.0-2017
 */
public abstract class AbstractLocalAsyncWorker extends AbstractLocalWorker {

    /**
     * Construct an <code>AbstractLocalAsyncWorker</code> with the worker role and context.
     *
     * @param role The responsibility of worker in cluster, more than one workers can have same responsibility which use
     * to provide load balancing ability.
     * @param clusterContext See {@link ClusterWorkerContext}
     * @param selfContext See {@link LocalWorkerContext}
     */
    public AbstractLocalAsyncWorker(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    /**
     * The asynchronous worker always use to persistence data into db, this is the end of the streaming,
     * so usually no need to create the next worker instance at the time of this worker instance create.
     *
     * @throws ProviderNotFoundException When worker provider not found, it will be throw this exception.
     */
    @Override
    public void preStart() throws ProviderNotFoundException {
    }

    /**
     * Receive message
     *
     * @param message The persistence data or metric data.
     * @throws WorkerException The Exception happen in {@link #onWork(Object)}
     */
    final public void allocateJob(Object message) throws WorkerException {
        onWork(message);
    }

    /**
     * The data process logic in this method.
     *
     * @param message Cast the message object to a expect subclass.
     * @throws WorkerException Don't handle the exception, throw it.
     */
    protected abstract void onWork(Object message) throws WorkerException;
}
