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

package org.apache.skywalking.oap.server.core;

import java.util.*;
import lombok.*;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

/**
 * @author peng-yongsheng
 */
@Getter
public class CoreModuleConfig extends ModuleConfig {
    @Setter private String role = "Mixed";
    @Setter private String nameSpace;
    @Setter private String restHost;
    @Setter private int restPort;
    @Setter private int jettySelectors = 1;
    @Setter private String restContextPath;
    @Setter private String gRPCHost;
    @Setter private int gRPCPort;
    @Setter private int maxConcurrentCallsPerConnection;
    @Setter private int maxMessageSize;
    @Setter private boolean enableDatabaseSession;
    private final List<String> downsampling;
    /**
     * The period of doing data persistence.
     * Unit is second.
     */
    @Setter private long persistentPeriod = 3;
    @Setter private boolean enableDataKeeperExecutor = true;
    @Setter private int dataKeeperExecutePeriod = 5;
    @Setter private int recordDataTTL;
    @Setter private int minuteMetricsDataTTL;
    @Setter private int hourMetricsDataTTL;
    @Setter private int dayMetricsDataTTL;
    @Setter private int monthMetricsDataTTL;
    @Setter private int gRPCThreadPoolSize;
    @Setter private int gRPCThreadPoolQueueSize;
    /**
     * Timeout for cluster internal communication, in seconds.
     */
    @Setter private int remoteTimeout = 20;

    CoreModuleConfig() {
        this.downsampling = new ArrayList<>();
    }

    public DataTTLConfig getDataTTL() {
        DataTTLConfig dataTTLConfig = new DataTTLConfig();
        dataTTLConfig.setRecordDataTTL(recordDataTTL);
        dataTTLConfig.setMinuteMetricsDataTTL(minuteMetricsDataTTL);
        dataTTLConfig.setHourMetricsDataTTL(hourMetricsDataTTL);
        dataTTLConfig.setDayMetricsDataTTL(dayMetricsDataTTL);
        dataTTLConfig.setMonthMetricsDataTTL(monthMetricsDataTTL);
        return dataTTLConfig;
    }

    public enum Role {
        Mixed, Receiver, Aggregator
    }
}
