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

package org.skywalking.apm.collector.stream.worker.base;

import org.skywalking.apm.collector.core.data.Data;
import org.skywalking.apm.collector.remote.service.RemoteClientService;
import org.skywalking.apm.collector.storage.service.DAOService;

/**
 * The <code>AbstractRemoteWorkerProvider</code> implementations represent providers,
 * which create instance of cluster workers whose implemented {@link AbstractRemoteWorker}.
 * <p>
 *
 * @author peng-yongsheng
 * @since v3.0-2017
 */
public abstract class AbstractRemoteWorkerProvider<INPUT extends Data, OUTPUT extends Data, WorkerType extends AbstractRemoteWorker<INPUT, OUTPUT>> extends AbstractWorkerProvider<INPUT, OUTPUT, WorkerType> {

    private final DAOService daoService;
    private final RemoteClientService remoteClientService;

    public AbstractRemoteWorkerProvider(DAOService daoService, RemoteClientService remoteClientService) {
        this.daoService = daoService;
        this.remoteClientService = remoteClientService;
    }

    /**
     * Create the worker instance into akka system, the akka system will control the cluster worker life cycle.
     *
     * @return The created worker reference. See {@link RemoteWorkerRef}
     * @throws ProviderNotFoundException This worker instance attempted to find a provider which use to create another
     * worker instance, when the worker provider not find then Throw this Exception.
     */
    @Override final public WorkerRef create(WorkerCreateListener workerCreateListener) {
        WorkerType remoteWorker = workerInstance(daoService);
        workerCreateListener.addWorker(remoteWorker);
        RemoteWorkerRef<INPUT, OUTPUT> workerRef = new RemoteWorkerRef<>(remoteWorker);
        return workerRef;
    }

    public final RemoteWorkerRef create(String host, int port) {
        RemoteWorkerRef<INPUT, OUTPUT> workerRef = new RemoteWorkerRef<>(null, remoteClientService.create(host, port));
        return workerRef;
    }
}
