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

package org.apache.skywalking.oap.server.core.cache;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage.AsyncProfilerTaskRecord;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTask;
import org.apache.skywalking.oap.server.core.storage.profiling.asyncprofiler.IAsyncProfilerTaskQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.pprof.IPprofTaskQueryDAO;
import org.apache.skywalking.oap.server.core.query.type.PprofTask;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.RunnableWithExceptionProtection;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.DisableRegister;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.profiling.pprof.storage.PprofTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.trace.ProfileTaskRecord;
import org.apache.skywalking.oap.server.core.query.type.ProfileTask;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.cache.INetworkAddressAliasDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.trace.IProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;

@Slf4j
public enum CacheUpdateTimer {
    INSTANCE;

    private AsyncProfilerTaskCache asyncProfilerTaskCache;
    private IAsyncProfilerTaskQueryDAO asyncProfilerTaskQueryDAO;
    private PprofTaskCache pprofTaskCache;
    private IPprofTaskQueryDAO pprofTaskQueryDAO;

    private int ttl = 10;

    public void start(ModuleDefineHolder moduleDefineHolder, int ttl) {
        log.info("Cache updateServiceInventory timer start");

        final long timeInterval = 10;

        Executors.newSingleThreadScheduledExecutor()
                 .scheduleAtFixedRate(
                     new RunnableWithExceptionProtection(() -> update(moduleDefineHolder), t -> log
                         .error("Cache update failure.", t)), 1, timeInterval, TimeUnit.SECONDS);
        this.ttl = ttl;

    }

    private void update(ModuleDefineHolder moduleDefineHolder) {
        updateNetAddressAliasCache(moduleDefineHolder);
        // Profile could be disabled by the OAL script. Only load the task when it is activated.
        if (!DisableRegister.INSTANCE.include(ProfileTaskRecord.INDEX_NAME)) {
            updateProfileTask(moduleDefineHolder);
        }

        if (!DisableRegister.INSTANCE.include(AsyncProfilerTaskRecord.INDEX_NAME)) {
            updateAsyncProfilerTask(moduleDefineHolder);
        }

        if (!DisableRegister.INSTANCE.include(PprofTaskRecord.INDEX_NAME)) {
            updatePprofTask(moduleDefineHolder);
        }
    }

    /**
     * Update the cached data updated in last 1 minutes.
     */
    private void updateNetAddressAliasCache(ModuleDefineHolder moduleDefineHolder) {
        INetworkAddressAliasDAO networkAddressAliasDAO = moduleDefineHolder.find(StorageModule.NAME)
                                                                           .provider()
                                                                           .getService(
                                                                               INetworkAddressAliasDAO.class);
        NetworkAddressAliasCache addressInventoryCache = moduleDefineHolder.find(CoreModule.NAME)
                                                                           .provider()
                                                                           .getService(NetworkAddressAliasCache.class);
        long loadStartTime;
        if (addressInventoryCache.currentSize() == 0) {
            /**
             * As a new start process, load all known network alias information.
             */
            loadStartTime = TimeBucket.getMinuteTimeBucket(System.currentTimeMillis() - 60_000L * 60 * 24 * ttl);
        } else {
            loadStartTime = TimeBucket.getMinuteTimeBucket(System.currentTimeMillis() - 60_000L * 10);
        }
        List<NetworkAddressAlias> addressInventories = networkAddressAliasDAO.loadLastUpdate(loadStartTime);

        addressInventoryCache.load(addressInventories);
    }

    /**
     * update all profile task list for each service
     */
    private void updateProfileTask(ModuleDefineHolder moduleDefineHolder) {
        IProfileTaskQueryDAO profileTaskQueryDAO = moduleDefineHolder.find(StorageModule.NAME)
                                                                     .provider()
                                                                     .getService(IProfileTaskQueryDAO.class);
        ProfileTaskCache profileTaskCache = moduleDefineHolder.find(CoreModule.NAME)
                                                              .provider()
                                                              .getService(ProfileTaskCache.class);
        try {
            final List<ProfileTask> taskList = profileTaskQueryDAO.getTaskList(
                null, null, profileTaskCache.getCacheStartTimeBucket(), profileTaskCache
                    .getCacheEndTimeBucket(), null);

            taskList.stream().collect(Collectors.groupingBy(t -> t.getServiceId())).entrySet().stream().forEach(e -> {
                final String serviceId = e.getKey();
                final List<ProfileTask> profileTasks = e.getValue();

                profileTaskCache.saveTaskList(serviceId, profileTasks);
            });
        } catch (IOException e) {
            log.warn("Unable to update profile task cache", e);
        }
    }

    private AsyncProfilerTaskCache getAsyncProfilerTaskCache(ModuleDefineHolder moduleDefineHolder) {
        if (asyncProfilerTaskCache == null) {
            asyncProfilerTaskCache = moduleDefineHolder.find(CoreModule.NAME)
                    .provider()
                    .getService(AsyncProfilerTaskCache.class);
        }
        return asyncProfilerTaskCache;
    }

    private IAsyncProfilerTaskQueryDAO getAsyncProfilerTaskQueryDAO(ModuleDefineHolder moduleDefineHolder) {
        if (asyncProfilerTaskQueryDAO == null) {
            asyncProfilerTaskQueryDAO = moduleDefineHolder.find(StorageModule.NAME)
                    .provider()
                    .getService(IAsyncProfilerTaskQueryDAO.class);
        }
        return asyncProfilerTaskQueryDAO;
    }

    private void updateAsyncProfilerTask(ModuleDefineHolder moduleDefineHolder) {
        AsyncProfilerTaskCache taskCache = getAsyncProfilerTaskCache(moduleDefineHolder);
        IAsyncProfilerTaskQueryDAO taskQueryDAO = getAsyncProfilerTaskQueryDAO(moduleDefineHolder);
        try {
            List<AsyncProfilerTask> taskList = taskQueryDAO.getTaskList(
                    null, taskCache.getCacheStartTimeBucket(), taskCache.getCacheEndTimeBucket(), null
            );
            if (CollectionUtils.isEmpty(taskList)) {
                return;
            }
            for (AsyncProfilerTask task : taskList) {
                taskCache.saveTask(task.getServiceId(), task);
            }

        } catch (IOException e) {
            log.warn("Unable to update async profiler task cache", e);
        }

        return;
    }

    private PprofTaskCache getPprofTaskCache(ModuleDefineHolder moduleDefineHolder) {
        if (pprofTaskCache == null) {
            pprofTaskCache = moduleDefineHolder.find(CoreModule.NAME)
                    .provider()
                    .getService(PprofTaskCache.class);
        }
        return pprofTaskCache;
    }

    private IPprofTaskQueryDAO getPprofTaskQueryDAO(ModuleDefineHolder moduleDefineHolder) {
        if (pprofTaskQueryDAO == null) {
            pprofTaskQueryDAO = moduleDefineHolder.find(StorageModule.NAME)
                    .provider()
                    .getService(IPprofTaskQueryDAO.class);
        }
        return pprofTaskQueryDAO;
    }

    private void updatePprofTask(ModuleDefineHolder moduleDefineHolder) {
        PprofTaskCache taskCache = getPprofTaskCache(moduleDefineHolder);
        IPprofTaskQueryDAO taskQueryDAO = getPprofTaskQueryDAO(moduleDefineHolder);

        try {
            List<PprofTask> taskList = taskQueryDAO.getTaskList(
                    null, taskCache.getCacheStartTimeBucket(), taskCache.getCacheEndTimeBucket(), null
            );
            if (CollectionUtils.isEmpty(taskList)) {
                return;
            }

            for (PprofTask task : taskList) {
                taskCache.saveTask(task.getServiceId(), task);
            }

        } catch (IOException e) {
            log.warn("Unable to update pprof task cache", e);
        }
    }
}