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

import org.skywalking.apm.collector.client.grpc.GRPCClient;

/**
 * The <code>AbstractRemoteWorkerProvider</code> implementations represent providers,
 * which create instance of cluster workers whose implemented {@link AbstractRemoteWorker}.
 * <p>
 *
 * @author peng-yongsheng
 * @since v3.0-2017
 */
public abstract class AbstractRemoteWorkerProvider<T extends AbstractRemoteWorker> extends AbstractWorkerProvider<T> {

    /**
     * Create the worker instance into akka system, the akka system will control the cluster worker life cycle.
     *
     * @return The created worker reference. See {@link RemoteWorkerRef}
     * @throws ProviderNotFoundException This worker instance attempted to find a provider which use to create another
     * worker instance, when the worker provider not find then Throw this Exception.
     */
    @Override final public WorkerRef create() {
        T clusterWorker = workerInstance(getClusterContext());
        RemoteWorkerRef workerRef = new RemoteWorkerRef(role(), clusterWorker);
        getClusterContext().put(workerRef);
        return workerRef;
    }

    public final RemoteWorkerRef create(GRPCClient client) {
        RemoteWorkerRef workerRef = new RemoteWorkerRef(role(), client);
        getClusterContext().put(workerRef);
        return workerRef;
    }
}
