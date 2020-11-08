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
 */

package org.apache.skywalking.apm.agent.core.pool.connections;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.boot.DefaultNamedThreadFactory;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.meter.MeterService;
import org.apache.skywalking.apm.util.StringUtil;

import static org.apache.skywalking.apm.agent.core.conf.Config.ConnectionPool.INTERVAL;

@DefaultImplementor
public class ConnectionPoolService implements BootService {
    public static final Map<String, ConnectionPoolInfoImpl> POOL_AND_METER_IDS_MAPPING = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduledExecutorService;

    @Override
    public void prepare() throws Throwable {
        if (Config.ConnectionPool.ACTIVE) {
            scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(
                new DefaultNamedThreadFactory("ConnectionPoolMetricFetcher")
            );

            scheduledExecutorService.scheduleAtFixedRate(() -> {
                if (POOL_AND_METER_IDS_MAPPING.isEmpty()) {
                    return;
                }

                POOL_AND_METER_IDS_MAPPING.forEach((s, monitorPoolRuntimeInfo) -> {
                    try {
                        monitorPoolRuntimeInfo.recordPoolCapabilityMetricValue();
                    } catch (ObjectHadBeenRecycledException e) {
                        monitorPoolRuntimeInfo.unregisterFromMeterSystem();
                    }
                });
            }, 0, INTERVAL, TimeUnit.SECONDS);
        }
    }

    @Override
    public void boot() throws Throwable {

    }

    @Override
    public void onComplete() throws Throwable {

    }

    @Override
    public void shutdown() throws Throwable {
        if (Config.ConnectionPool.ACTIVE) {
            POOL_AND_METER_IDS_MAPPING.clear();
            scheduledExecutorService.shutdown();
        }
    }

    public <T> ConnectionPoolInfo startMonitor(final String poolId,
                                               final PoolCapabilityMetricValueRecorder<T> recorder) {
        if (!Config.ConnectionPool.ACTIVE) {
            return new ConnectionPoolInfo() {
            };
        }

        if (StringUtil.isBlank(poolId) || recorder == null) {
            throw new IllegalArgumentException("poolId or recordMetricValueFetcher is null");
        }

        ConnectionPoolInfo monitorPoolRuntimeInfo = new ConnectionPoolInfoImpl(poolId, recorder);
        monitorPoolRuntimeInfo.registerToMeterSystem(ServiceManager.INSTANCE.findService(MeterService.class));
        POOL_AND_METER_IDS_MAPPING.put(poolId, monitorPoolRuntimeInfo);
        return monitorPoolRuntimeInfo;
    }
}
