/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.stream.worker;

/**
 * The <code>AbstractRemoteWorker</code> implementations represent workers,
 * which receive remote messages.
 * <p>
 * Usually, the implementations are doing persistent, or aggregate works.
 *
 * @author peng-yongsheng
 * @since v3.0-2017
 */
public abstract class AbstractRemoteWorker extends AbstractWorker<RemoteWorkerRef> {

    private RemoteWorkerRef workerRef;

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

    @Override protected final RemoteWorkerRef getSelf() {
        return workerRef;
    }

    @Override protected final void putSelfRef(RemoteWorkerRef workerRef) {
        this.workerRef = workerRef;
    }
}
