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

import org.skywalking.apm.collector.core.framework.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractWorker<S extends WorkerRef> implements Executor {

    private final Logger logger = LoggerFactory.getLogger(AbstractWorker.class);

    private final Role role;

    private final ClusterWorkerContext clusterContext;

    public AbstractWorker(Role role, ClusterWorkerContext clusterContext) {
        this.role = role;
        this.clusterContext = clusterContext;
    }

    @Override public final void execute(Object message) {
        try {
            onWork(message);
        } catch (WorkerException e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * The data process logic in this method.
     *
     * @param message Cast the message object to a expect subclass.
     * @throws WorkerException Don't handle the exception, throw it.
     */
    protected abstract void onWork(Object message) throws WorkerException;

    public abstract void preStart() throws ProviderNotFoundException;

    final public ClusterWorkerContext getClusterContext() {
        return clusterContext;
    }

    final public Role getRole() {
        return role;
    }

    protected abstract S getSelf();

    protected abstract void putSelfRef(S workerRef);
}
