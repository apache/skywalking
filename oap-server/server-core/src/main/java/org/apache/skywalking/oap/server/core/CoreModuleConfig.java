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
import org.apache.skywalking.oap.server.core.config.SearchableTracesTagsWatcher;
import org.apache.skywalking.oap.server.core.source.ScopeDefaultColumn;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

@Getter
public class CoreModuleConfig extends ModuleConfig {
    private String role = "Mixed";
    private String namespace;
    private String restHost;
    private int restPort;
    private String restContextPath;
    private int restMaxThreads = 200;
    private long restIdleTimeOut = 30000;
    private int restAcceptQueueSize = 0;

    private String gRPCHost;
    private int gRPCPort;
    private boolean gRPCSslEnabled = false;
    private String gRPCSslKeyPath;
    private String gRPCSslCertChainPath;
    private String gRPCSslTrustedCAPath;
    private int maxConcurrentCallsPerConnection;
    private int maxMessageSize;
    private int topNReportPeriod;
    /**
     * The period of L1 aggregation flush. Unit is ms.
     */
    private long l1FlushPeriod = 500;
    /**
     * The threshold of session time. Unit is ms. Default value is 70s.
     */
    private long storageSessionTimeout = 70_000;
    private final List<String> downsampling;
    /**
     * The period of doing data persistence. Unit is second.
     */
    @Setter
    private int persistentPeriod = 25;

    private boolean enableDataKeeperExecutor = true;

    private int dataKeeperExecutePeriod = 5;
    /**
     * The time to live of all metrics data. Unit is day.
     */

    private int metricsDataTTL = 7;
    /**
     * The time to live of all record data, including tracing. Unit is Day.
     */

    private int recordDataTTL = 3;

    private int gRPCThreadPoolSize;

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
     * Query the eBPF Profiling data max duration(second) from database.
     */
    private int maxDurationOfQueryEBPFProfilingData = 30;
    /**
     * Thread Count of query the eBPF Profiling data.
     */
    private int maxThreadCountOfQueryEBPFProfilingData = Runtime.getRuntime().availableProcessors();
    /**
     * Extra model column are the column defined by {@link ScopeDefaultColumn.DefinedByField#requireDynamicActive()} ==
     * true. These columns of model are not required logically in aggregation or further query, and it will cause more
     * load for memory, network of OAP and storage.
     *
     * But, being activated, user could see the name in the storage entities, which make users easier to use 3rd party
     * tool, such as Kibana-&gt;ES, to query the data by themselves.
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
    @Setter
    @Getter
    private SearchableTracesTagsWatcher searchableTracesTagsWatcher;

    /**
     * Define the set of logs tag keys, which should be searchable through the GraphQL.
     *
     * @since 8.4.0
     */
    @Setter
    @Getter
    private String searchableLogsTags = "";
    /**
     * Define the set of Alarm tag keys, which should be searchable through the GraphQL.
     *
     * @since 8.6.0
     */
    @Setter
    @Getter
    private String searchableAlarmTags = "";
    /**
     * The max size of tags keys for autocomplete select.
     *
     * @since 9.1.0
     */
    @Setter
    @Getter
    private int autocompleteTagKeysQueryMaxSize = 100;
    /**
     * The max size of tags values for autocomplete select.
     *
     * @since 9.1.0
     */
    @Setter
    @Getter
    private int autocompleteTagValuesQueryMaxSize = 100;
    /**
     * The number of threads used to prepare metrics data to the storage.
     *
     * @since 8.7.0
     */
    @Setter
    @Getter
    private int prepareThreads = 2;

    @Getter
    @Setter
    private boolean enableEndpointNameGroupingByOpenapi = true;

    /**
     * The maximum size in bytes allowed for request headers.
     * Use -1 to disable it.
     */
    private int httpMaxRequestHeaderSize = 8192;

    /**
     * The period of HTTP URI pattern recognition. Unit is second.
     * @since 9.5.0
     */
    private int syncPeriodHttpUriRecognitionPattern = 10;

    /**
     * The training period of HTTP URI pattern recognition. Unit is second.
     * @since 9.5.0
     */
    private int trainingPeriodHttpUriRecognitionPattern = 60;

    /**
     * The max number of HTTP URIs per service for further URI pattern recognition.
     * @since 9.5.0
     */
    private int maxHttpUrisNumberPerService = 3000;

    /**
     * The UI menu should activate fetch interval, default 20s
     */
    private int uiMenuRefreshInterval = 20;

    /**
     * The service cache refresh interval, default 10s
     */
    @Setter
    @Getter
    private int serviceCacheRefreshInterval = 10;

    /**
     * If disable the hierarchy, the service and instance hierarchy relation will not be built.
     * And the query of hierarchy will return empty result.
     */
    @Setter
    @Getter
    private boolean enableHierarchy = true;

    /**
     * The int value of the max heap memory usage percent.
     * The default value is 85%.
     */
    @Getter
    private long maxHeapMemoryUsagePercent = 85;

    /**
     * The long value of the max direct memory usage.
     * The default max value is -1, representing no limit.
     */
    @Getter
    private long maxDirectMemoryUsage = -1;

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
