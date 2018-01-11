/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.collector.analysis.worker.model.base;

import org.apache.skywalking.apm.collector.core.data.RemoteData;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.remote.service.RemoteSenderService;

/**
 * The <code>AbstractRemoteWorkerProvider</code> implementations represent providers,
 * which create instance of cluster workers whose implemented {@link AbstractRemoteWorker}.
 * <p>
 *
 * @author peng-yongsheng
 * @since v3.0-2017
 */
public abstract class AbstractRemoteWorkerProvider<INPUT extends RemoteData, OUTPUT extends RemoteData, WORKER_TYPE extends AbstractRemoteWorker<INPUT, OUTPUT>> extends AbstractWorkerProvider<INPUT, OUTPUT, WORKER_TYPE> {

    private final RemoteSenderService remoteSenderService;
    private final int graphId;

    public AbstractRemoteWorkerProvider(ModuleManager moduleManager, RemoteSenderService remoteSenderService,
        int graphId) {
        super(moduleManager);
        this.remoteSenderService = remoteSenderService;
        this.graphId = graphId;
    }

    /**
     * Create the worker instance into akka system, the akka system will control the cluster worker life cycle.
     *
     * @return The created worker reference. See {@link RemoteWorkerRef} worker instance, when the worker provider not
     * find then Throw this Exception.
     */
    @Override final public RemoteWorkerRef<INPUT, OUTPUT> create(WorkerCreateListener workerCreateListener) {
        WORKER_TYPE remoteWorker = workerInstance(getModuleManager());
        workerCreateListener.addWorker(remoteWorker);
        return new RemoteWorkerRef<>(remoteWorker, remoteSenderService, graphId);
    }
}
