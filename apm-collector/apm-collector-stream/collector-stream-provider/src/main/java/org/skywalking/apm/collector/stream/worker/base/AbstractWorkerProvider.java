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

import org.skywalking.apm.collector.cache.CacheServiceManager;
import org.skywalking.apm.collector.core.data.Data;
import org.skywalking.apm.collector.storage.service.DAOService;

/**
 * @author peng-yongsheng
 */
<<<<<<< HEAD
public abstract class AbstractWorkerProvider<INPUT extends Data, OUTPUT extends Data, WorkerType extends AbstractWorker<INPUT, OUTPUT>> implements Provider {

    private final DAOService daoService;
    private final CacheServiceManager cacheServiceManager;

    public AbstractWorkerProvider(DAOService daoService, CacheServiceManager cacheServiceManager) {
        this.daoService = daoService;
        this.cacheServiceManager = cacheServiceManager;
    }

    public final DAOService getDaoService() {
        return daoService;
    }

    public final CacheServiceManager getCacheServiceManager() {
        return cacheServiceManager;
    }

    public abstract WorkerType workerInstance(DAOService daoService, CacheServiceManager cacheServiceManager);
=======
public abstract class AbstractWorkerProvider<INPUT extends Data, OUTPUT extends Data, WORKER_TYPE extends AbstractWorker<INPUT, OUTPUT>> implements Provider {
    public abstract WORKER_TYPE workerInstance(DAOService daoService);
>>>>>>> 0c17906c3c1c41752e1ec38b37d9e0dec22503ca
}
