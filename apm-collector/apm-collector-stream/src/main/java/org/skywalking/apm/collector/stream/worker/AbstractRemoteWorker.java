package org.skywalking.apm.collector.stream.worker;

/**
 * The <code>AbstractRemoteWorker</code> implementations represent workers,
 * which receive remote messages.
 * <p>
 * Usually, the implementations are doing persistent, or aggregate works.
 *
 * @author pengys5
 * @since v3.0-2017
 */
public abstract class AbstractRemoteWorker extends AbstractWorker {

    /**
     * Construct an <code>AbstractRemoteWorker</code> with the worker role and context.
     *
     * @param role If multi-workers are for load balance, they should be more likely called worker instance. Meaning,
     * each worker have multi instances.
     * @param clusterContext See {@link ClusterWorkerContext}
     */
    protected AbstractRemoteWorker(Role role, ClusterWorkerContext clusterContext) {
        super(role, clusterContext);
    }

    /**
     * This method use for message producer to call for send message.
     *
     * @param message The persistence data or metric data.
     * @throws Exception The Exception happen in {@link #onWork(Object)}
     */
    final public void allocateJob(Object message) throws WorkerInvokeException {
        try {
            onWork(message);
        } catch (WorkerException e) {
            throw new WorkerInvokeException(e.getMessage(), e.getCause());
        }
    }

    /**
     * This method use for message receiver to analyse message.
     *
     * @param message Cast the message object to a expect subclass.
     * @throws Exception Don't handle the exception, throw it.
     */
    protected abstract void onWork(Object message) throws WorkerException;
}
