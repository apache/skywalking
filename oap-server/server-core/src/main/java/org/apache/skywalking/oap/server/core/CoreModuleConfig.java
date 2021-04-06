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
    private String role = "Mixed";
    private String nameSpace;
    private String restHost;
    private int restPort;
    private String restContextPath;
    private int restMinThreads = 1;
    private int restMaxThreads = 200;
    private long restIdleTimeOut = 30000;
    private int restAcceptorPriorityDelta = 0;
    private int restAcceptQueueSize = 0;

    private String gRPCHost;
    private int gRPCPort;
    private boolean gRPCSslEnabled = false;
    private String gRPCSslKeyPath;
    private String gRPCSslCertChainPath;
    private String gRPCSslTrustedCAPath;
    private int maxConcurrentCallsPerConnection;
    private int maxMessageSize;
    private boolean enableDatabaseSession;
    private int topNReportPeriod;
    private final List<String> downsampling;
    /**
     * The period of doing data persistence. Unit is second.
     */

    private long persistentPeriod = 3;

    private boolean enableDataKeeperExecutor = true;

    private int dataKeeperExecutePeriod = 5;
    /**
     * The time to live of all metrics data. Unit is day.
     */

    private int metricsDataTTL = 3;
    /**
     * The time to live of all record data, including tracing. Unit is Day.
     */

    private int recordDataTTL = 7;

    private int gRPCThreadPoolSize;

    private int gRPCThreadPoolQueueSize;
    /**
     * Timeout for cluster internal communication, in seconds.
     */

    private int remoteTimeout = 20;
    /**
     * The size of network address alias.
     */
    private long maxSizeOfNetworkAddressAlias = 1_000_000L;
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
     * The max length of the service name.
     */
    private int serviceNameMaxLength = 70;
    /**
     * The max length of the service instance name.
     */
    private int instanceNameMaxLength = 70;
    /**
     * The max length of the endpoint name.
     *
     * <p>NOTICE</p>
     * In the current practice, we don't recommend the length over 190.
     */
    private int endpointNameMaxLength = 150;
    /**
     * Define the set of span tag keys, which should be searchable through the GraphQL.
     *
     * @since 8.2.0
     */
    @Setter
    @Getter
    private String searchableTracesTags = DEFAULT_SEARCHABLE_TAG_KEYS;
    /**
     * Define the set of logs tag keys, which should be searchable through the GraphQL.
     *
     * @since 8.4.0
     */
    @Setter
    @Getter
    private String searchableLogsTags = "";

    /**
     * The number of threads used to synchronously refresh the metrics data to the storage.
     *
     * @since 8.5.0
     */
    @Setter
    @Getter
    private int syncThreads = 2;

    /**
     * The maximum number of processes supported for each synchronous storage operation. When the number of the flush
     * data is greater than this value, it will be assigned to multiple cores for execution.
     */
    @Getter
    @Setter
    private int maxSyncOperationNum = 50000;

    public CoreModuleConfig() {
        this.downsampling = new ArrayList<>();
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
        Aggregator;

        public static Role fromName(String name) {
            for (Role role : Role.values()) {
                if (role.name().equalsIgnoreCase(name)) {
                    return role;
                }
            }
            return Mixed;
        }
    }

    /**
     * SkyWalking Java Agent provides the recommended tag keys for other language agents or SDKs. This field declare the
     * recommended keys should be searchable.
     */
    private static final String DEFAULT_SEARCHABLE_TAG_KEYS = String.join(
        Const.COMMA,
        "http.method",
        "status_code",
        "db.type",
        "db.instance",
        "mq.queue",
        "mq.topic",
        "mq.broker"
    );
}
