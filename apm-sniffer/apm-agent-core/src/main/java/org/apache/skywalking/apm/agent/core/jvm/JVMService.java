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

package org.apache.skywalking.apm.agent.core.jvm;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.boot.DefaultNamedThreadFactory;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.jvm.cpu.CPUProvider;
import org.apache.skywalking.apm.agent.core.jvm.gc.GCProvider;
import org.apache.skywalking.apm.agent.core.jvm.memory.MemoryProvider;
import org.apache.skywalking.apm.agent.core.jvm.memorypool.MemoryPoolProvider;
import org.apache.skywalking.apm.agent.core.jvm.thread.ThreadProvider;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelManager;
import org.apache.skywalking.apm.network.language.agent.v3.JVMMetric;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;

/**
 * The <code>JVMService</code> represents a timer, which collectors JVM cpu, memory, memorypool and gc info, and send
 * the collected info to Collector through the channel provided by {@link GRPCChannelManager}
 */
@DefaultImplementor
public class JVMService implements BootService, Runnable {
    private static final ILog LOGGER = LogManager.getLogger(JVMService.class);
    private volatile ScheduledFuture<?> collectMetricFuture;
    private volatile ScheduledFuture<?> sendMetricFuture;
    private JVMMetricsSender sender;

    @Override
    public void prepare() throws Throwable {
        sender = ServiceManager.INSTANCE.findService(JVMMetricsSender.class);
    }

    @Override
    public void boot() throws Throwable {
        collectMetricFuture = Executors.newSingleThreadScheduledExecutor(
            new DefaultNamedThreadFactory("JVMService-produce"))
                                       .scheduleAtFixedRate(new RunnableWithExceptionProtection(
                                           this,
                                           new RunnableWithExceptionProtection.CallbackWhenException() {
                                               @Override
                                               public void handle(Throwable t) {
                                                   LOGGER.error("JVMService produces metrics failure.", t);
                                               }
                                           }
                                       ), 0, 1, TimeUnit.SECONDS);
        sendMetricFuture = Executors.newSingleThreadScheduledExecutor(
            new DefaultNamedThreadFactory("JVMService-consume"))
                                    .scheduleAtFixedRate(new RunnableWithExceptionProtection(
                                        sender,
                                        new RunnableWithExceptionProtection.CallbackWhenException() {
                                            @Override
                                            public void handle(Throwable t) {
                                                LOGGER.error("JVMService consumes and upload failure.", t);
                                            }
                                        }
                                    ), 0, 1, TimeUnit.SECONDS);
    }

    @Override
    public void onComplete() throws Throwable {

    }

    @Override
    public void shutdown() throws Throwable {
        collectMetricFuture.cancel(true);
        sendMetricFuture.cancel(true);
    }

    @Override
    public void run() {
        long currentTimeMillis = System.currentTimeMillis();
        try {
            JVMMetric.Builder jvmBuilder = JVMMetric.newBuilder();
            jvmBuilder.setTime(currentTimeMillis);
            jvmBuilder.setCpu(CPUProvider.INSTANCE.getCpuMetric());
            jvmBuilder.addAllMemory(MemoryProvider.INSTANCE.getMemoryMetricList());
            jvmBuilder.addAllMemoryPool(MemoryPoolProvider.INSTANCE.getMemoryPoolMetricsList());
            jvmBuilder.addAllGc(GCProvider.INSTANCE.getGCList());
            jvmBuilder.setThread(ThreadProvider.INSTANCE.getThreadMetrics());

            sender.offer(jvmBuilder.build());
        } catch (Exception e) {
            LOGGER.error(e, "Collect JVM info fail.");
        }
    }
}
