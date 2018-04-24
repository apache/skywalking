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

package org.apache.skywalking.apm.agent.core.remote;

import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.boot.DefaultNamedThreadFactory;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.conf.RemoteDownstreamConfig;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;

import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The <code>CollectorDiscoveryService</code> is responsible for start {@link DiscoveryRestServiceClient}.
 *
 * @author wusheng
 */
@DefaultImplementor
public class CollectorDiscoveryService implements BootService {
    private static final ILog logger = LogManager.getLogger(CollectorDiscoveryService.class);
    private ScheduledFuture<?> future;

    @Override
    public void prepare() {

    }

    @Override
    public void boot() {
        DiscoveryRestServiceClient discoveryRestServiceClient = new DiscoveryRestServiceClient();
        if (discoveryRestServiceClient.hasNamingServer()) {
            discoveryRestServiceClient.run();
            future = Executors.newSingleThreadScheduledExecutor(
                    new DefaultNamedThreadFactory("CollectorDiscoveryService"))
                    .scheduleAtFixedRate(new RunnableWithExceptionProtection(discoveryRestServiceClient, new RunnableWithExceptionProtection.CallbackWhenException() {
                        @Override
                        public void handle(Throwable t) {
                            logger.error("unexpected exception.", t);
                        }
                    }),
                            Config.Collector.DISCOVERY_CHECK_INTERVAL,
                            Config.Collector.DISCOVERY_CHECK_INTERVAL,
                            TimeUnit.SECONDS);
        } else {
            if (Config.Collector.DIRECT_SERVERS == null || Config.Collector.DIRECT_SERVERS.trim().length() == 0) {
                logger.error("Collector server and direct server addresses are both not set.");
                logger.error("Agent will not uplink any data.");
                return;
            }
            RemoteDownstreamConfig.Collector.GRPC_SERVERS = Arrays.asList(Config.Collector.DIRECT_SERVERS.split(","));
        }
    }

    @Override
    public void onComplete() throws Throwable {

    }

    @Override
    public void shutdown() throws Throwable {
        if (future != null) {
            future.cancel(true);
        }
    }
}
