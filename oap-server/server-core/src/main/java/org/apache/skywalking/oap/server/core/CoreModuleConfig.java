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
    @Setter private String nameSpace;
    @Setter private String restHost;
    @Setter private int restPort;
    @Setter private String restContextPath;
    @Setter private String gRPCBindHost;
    @Setter private String gRPCDiscoveryHost;
    @Setter private int gRPCPort;
    @Setter private int maxConcurrentCallsPerConnection;
    @Setter private int maxMessageSize;
    private final List<String> downsampling;
    @Setter private int recordDataTTL;
    @Setter private int minuteMetricsDataTTL;
    @Setter private int hourMetricsDataTTL;
    @Setter private int dayMetricsDataTTL;
    @Setter private int monthMetricsDataTTL;

    CoreModuleConfig() {
        this.downsampling = new ArrayList<>();
    }

    public DataTTL getDataTTL() {
        DataTTL dataTTL = new DataTTL();
        dataTTL.setRecordDataTTL(recordDataTTL);
        dataTTL.setMinuteMetricsDataTTL(minuteMetricsDataTTL);
        dataTTL.setHourMetricsDataTTL(hourMetricsDataTTL);
        dataTTL.setDayMetricsDataTTL(dayMetricsDataTTL);
        dataTTL.setMonthMetricsDataTTL(monthMetricsDataTTL);
        return dataTTL;
    }
}
