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

package org.apache.skywalking.apm.agent.core.meter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.boot.DefaultNamedThreadFactory;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;

/**
 * Agent core level service. It provides the register map for all available {@link BaseMeter} instances and schedules
 * the {@link MeterSender}
 */
@DefaultImplementor
public class MeterService implements BootService, Runnable {
    private static final ILog LOGGER = LogManager.getLogger(MeterService.class);

    // all meters
    private final ConcurrentHashMap<MeterId, BaseMeter> meterMap = new ConcurrentHashMap<>();

    // report meters
    private volatile ScheduledFuture<?> reportMeterFuture;

    private MeterSender sender;

    /**
     * Register the meter
     */
    public <T extends BaseMeter> T register(T meter) {
        if (meter == null) {
            return null;
        }
        if (meterMap.size() >= Config.Meter.MAX_METER_SIZE) {
            LOGGER.warn(
                "Already out of the meter system max size, will not report. meter name:{}", meter.getName());
            return meter;
        }

        final BaseMeter data = meterMap.putIfAbsent(meter.getId(), meter);
        return data == null ? meter : (T) data;
    }

    @Override
    public void prepare() {
        sender = ServiceManager.INSTANCE.findService(MeterSender.class);
    }

    @Override
    public void boot() {
        if (Config.Meter.ACTIVE) {
            reportMeterFuture = Executors.newSingleThreadScheduledExecutor(
                new DefaultNamedThreadFactory("MeterReportService")
            ).scheduleWithFixedDelay(new RunnableWithExceptionProtection(
                this,
                t -> LOGGER.error("Report meters failure.", t)
            ), 0, Config.Meter.REPORT_INTERVAL, TimeUnit.SECONDS);
        }
    }

    @Override
    public void onComplete() {
    }

    @Override
    public void shutdown() {
        if (reportMeterFuture != null) {
            reportMeterFuture.cancel(true);
        }
        // clear all of the meter report
        meterMap.clear();
    }

    @Override
    public void run() {
        if (meterMap.isEmpty()) {
            return;
        }
        sender.send(meterMap, this);
    }

}
