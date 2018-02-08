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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultNamedThreadFactory;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;

/**
 * The <code>CollectorDiscoveryService</code> is responsible for start {@link DiscoveryRestServiceClient}.
 *
 * @author wusheng
 */
public class CollectorDiscoveryService implements BootService {
    private static final ILog logger = LogManager.getLogger(CollectorDiscoveryService.class);
    private ScheduledFuture<?> future;

    @Override
    public void beforeBoot() throws Throwable {

    }

    @Override
    public void boot() throws Throwable {
        DiscoveryRestServiceClient discoveryRestServiceClient = new DiscoveryRestServiceClient();
        discoveryRestServiceClient.run();
        future = Executors.newSingleThreadScheduledExecutor(new DefaultNamedThreadFactory("CollectorDiscoveryService"))
            .scheduleAtFixedRate(new RunnableWithExceptionProtection(discoveryRestServiceClient,
                    new RunnableWithExceptionProtection.CallbackWhenException() {
                        @Override public void handle(Throwable t) {
                            logger.error("unexpected exception.", t);
                        }
                    }), Config.Collector.DISCOVERY_CHECK_INTERVAL,
                Config.Collector.DISCOVERY_CHECK_INTERVAL, TimeUnit.SECONDS);
    }

    @Override
    public void afterBoot() throws Throwable {

    }

    @Override
    public void shutdown() throws Throwable {
        future.cancel(true);
    }
}
