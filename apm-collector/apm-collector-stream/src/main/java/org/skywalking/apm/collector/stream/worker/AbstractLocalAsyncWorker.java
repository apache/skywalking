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

import org.skywalking.apm.collector.core.queue.QueueExecutor;

/**
 * The <code>AbstractLocalAsyncWorker</code> implementations represent workers,
 * which receive local asynchronous message.
 *
 * @author peng-yongsheng
 * @since v3.0-2017
 */
public abstract class AbstractLocalAsyncWorker extends AbstractWorker<LocalAsyncWorkerRef> implements QueueExecutor {

    private LocalAsyncWorkerRef workerRef;

    /**
     * Construct an <code>AbstractLocalAsyncWorker</code> with the worker role and context.
     *
     * @param role The responsibility of worker in cluster, more than one workers can have same responsibility which use
     * to provide load balancing ability.
     * @param clusterContext See {@link ClusterWorkerContext}
     */
    public AbstractLocalAsyncWorker(Role role, ClusterWorkerContext clusterContext) {
        super(role, clusterContext);
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

    @Override protected final LocalAsyncWorkerRef getSelf() {
        return workerRef;
    }

    @Override protected final void putSelfRef(LocalAsyncWorkerRef workerRef) {
        this.workerRef = workerRef;
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
}
