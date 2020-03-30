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

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.source.ScopeDefaultColumn;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

@Getter
public class CoreModuleConfig extends ModuleConfig {
    @Setter
    private String role = "Mixed";
    @Setter
    private String nameSpace;
    @Setter
    private String restHost;
    @Setter
    private int restPort;
    @Setter
    private int jettySelectors = 1;
    @Setter
    private String restContextPath;
    @Setter
    private String gRPCHost;
    @Setter
    private int gRPCPort;
    @Setter
    private boolean gRPCSslEnabled = false;
    @Setter
    private String gRPCSslKeyPath;
    @Setter
    private String gRPCSslCertChainPath;
    @Setter
    private String gRPCSslTrustedCAPath;
    @Setter
    private int maxConcurrentCallsPerConnection;
    @Setter
    private int maxMessageSize;
    @Setter
    private boolean enableDatabaseSession;
    @Setter
    private int topNReportPeriod;
    private final List<String> downsampling;
    /**
     * The period of doing data persistence. Unit is second.
     */
    @Setter
    private long persistentPeriod = 3;
    @Setter
    private boolean enableDataKeeperExecutor = true;
    @Setter
    private int dataKeeperExecutePeriod = 5;
    @Setter
    private int recordDataTTL;
    @Setter
    private int minuteMetricsDataTTL;
    @Setter
    private int hourMetricsDataTTL;
    @Setter
    private int dayMetricsDataTTL;
    @Setter
    private int monthMetricsDataTTL;
    @Setter
    private int gRPCThreadPoolSize;
    @Setter
    private int gRPCThreadPoolQueueSize;
    /**
     * Timeout for cluster internal communication, in seconds.
     */
    @Setter
    private int remoteTimeout = 20;
    /**
     * Following are cache settings for inventory(s)
     */
    private long maxSizeOfServiceInventory = 10_000L;
    private long maxSizeOfServiceInstanceInventory = 1_000_000L;
    private long maxSizeOfEndpointInventory = 1_000_000L;
    private long maxSizeOfNetworkInventory = 1_000_000L;
    /**
     * Following are cache setting for none stream(s)
     */
    private long maxSizeOfProfileTask = 10_000L;
    /**
     * Analyze profile snapshots paging size.
     */
    private int maxPageSizeOfQueryProfileSnapshot = 500;
    /**
     * Analyze profile snapshots max size.
     */
    private int maxSizeOfAnalyzeProfileSnapshot = 12000;
    /**
     * Extra model column are the column defined by {@link ScopeDefaultColumn.DefinedByField#requireDynamicActive()} ==
     * true. These columns of model are not required logically in aggregation or further query, and it will cause more
     * load for memory, network of OAP and storage.
     *
     * But, being activated, user could see the name in the storage entities, which make users easier to use 3rd party
     * tool, such as Kibana->ES, to query the data by themselves.
     */
    private boolean activeExtraModelColumns = false;
    /**
     * The max length of the endpoint name.
     *
     * <p>NOTICE</p>
     * In the current practice, we don't recommend the length over 190.
     */
    private int endpointNameMaxLength = 150;

    public CoreModuleConfig() {
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

    /**
     * OAP server could work in different roles.
     */
    public enum Role {
        /**
         * Default role. OAP works as the {@link #Receiver} and {@link #Aggregator}
         */
        Mixed,
        /**
         * Receiver mode OAP open the service to the agents, analysis and aggregate the results and forward the results
         * to {@link #Mixed} and {@link #Aggregator} roles OAP. The only exception is for {@link
         * org.apache.skywalking.oap.server.core.analysis.record.Record}, they don't require 2nd round distributed
         * aggregation, is being pushed into the storage from the receiver OAP directly.
         */
        Receiver,
        /**
         * Aggregator mode OAP receives data from {@link #Mixed} and {@link #Aggregator} OAP nodes, and do 2nd round
         * aggregation. Then save the final result to the storage.
         */
        Aggregator
    }
}
