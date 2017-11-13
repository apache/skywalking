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

package org.skywalking.apm.collector.agent.stream.worker.trace.serviceref;

import org.skywalking.apm.collector.cache.CacheServiceManager;
import org.skywalking.apm.collector.remote.service.RemoteSenderService;
import org.skywalking.apm.collector.remote.service.Selector;
import org.skywalking.apm.collector.storage.service.DAOService;
import org.skywalking.apm.collector.storage.table.serviceref.ServiceReference;
import org.skywalking.apm.collector.stream.worker.base.AbstractRemoteWorker;
import org.skywalking.apm.collector.stream.worker.base.AbstractRemoteWorkerProvider;
import org.skywalking.apm.collector.stream.worker.base.WorkerException;

/**
 * @author peng-yongsheng
 */
public class ServiceReferenceRemoteWorker extends AbstractRemoteWorker<ServiceReference, ServiceReference> {

    public ServiceReferenceRemoteWorker(DAOService daoService, CacheServiceManager cacheServiceManager) {
        super(daoService, cacheServiceManager);
    }

    @Override public int id() {
        return ServiceReferenceRemoteWorker.class.hashCode();
    }

    @Override protected void onWork(ServiceReference serviceReference) throws WorkerException {
        onNext(serviceReference);
    }

    @Override public Selector selector() {
        return Selector.HashCode;
    }

    public static class Factory extends AbstractRemoteWorkerProvider<ServiceReference, ServiceReference, ServiceReferenceRemoteWorker> {
        public Factory(DAOService daoService, CacheServiceManager cacheServiceManager,
            RemoteSenderService remoteSenderService, int graphId) {
            super(daoService, cacheServiceManager, remoteSenderService, graphId);
        }

        @Override
        public ServiceReferenceRemoteWorker workerInstance(DAOService daoService,
            CacheServiceManager cacheServiceManager) {
            return new ServiceReferenceRemoteWorker(getDaoService(), getCacheServiceManager());
        }
    }
}
