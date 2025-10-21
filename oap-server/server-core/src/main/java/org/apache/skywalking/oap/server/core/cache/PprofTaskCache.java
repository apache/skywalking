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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.query.type.PprofTask;
import org.apache.skywalking.oap.server.library.module.Service;

public class PprofTaskCache implements Service {
    private final Cache<String, List<PprofTask>> serviceId2taskCache;

    public PprofTaskCache(CoreModuleConfig moduleConfig) {
        long initialSize = moduleConfig.getMaxSizeOfPprofTask() / 10L;
        int initialCapacitySize = (int) (initialSize > Integer.MAX_VALUE ? Integer.MAX_VALUE : initialSize);

        serviceId2taskCache = CacheBuilder.newBuilder()
                                          .initialCapacity(initialCapacitySize)
                                          .maximumSize(moduleConfig.getMaxSizeOfProfileTask())
                                          // remove old pprof task data
                                          .expireAfterWrite(Duration.ofMinutes(1))
                                          .build();
    }

    public List<PprofTask> getPprofTaskList(String serviceId) {
        // read pprof task list from cache only, use cache update timer mechanism
        List<PprofTask> pprofTaskList = serviceId2taskCache.getIfPresent(serviceId);
        return pprofTaskList;
    }

    /**
     * save service task list
     */
    public void saveTaskList(String serviceId, List<PprofTask> taskList) {
        if (taskList == null) {
            taskList = Collections.emptyList();
        }

        serviceId2taskCache.put(serviceId, taskList);
    }

    /**
     * use for every db query, -5min start time
     */
    public long getCacheStartTimeBucket() {
        return TimeBucket.getRecordTimeBucket(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5));
    }

    /**
     * use for every db query, +5min end time(because search through task's create time)
     */
    public long getCacheEndTimeBucket() {
        return TimeBucket.getRecordTimeBucket(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5));
    }

}
