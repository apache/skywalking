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

package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

@Getter
@Setter
public class BanyanDBStorageConfig extends ModuleConfig {

    public static final String PROPERTY_GROUP_NAME = "property";

    private Global global = new Global();
    private RecordsNormal recordsNormal = new RecordsNormal();
    private RecordsSuper recordsSuper = new RecordsSuper();
    private MetricsMin metricsMin = new MetricsMin();
    private MetricsHour metricsHour = new MetricsHour();
    private MetricsDay metricsDay = new MetricsDay();
    private Metadata metadata = new Metadata();
    private Property property = new Property();

    public String[] getTargetArray() {
        return Iterables.toArray(
            Splitter.on(",").omitEmptyStrings().trimResults().split(this.global.targets), String.class);
    }

    @Getter
    @Setter
    public static class Global {
        /**
         * A comma-separated list of BanyanDB targets.
         *
         * @since 9.7.0
         */
        private String targets = "127.0.0.1:17912";
        /**
         * The maximum size of write entities in a single batch write call.
         */
        private int maxBulkSize = 5000;
        /**
         * Period of flush interval. In the timeunit of seconds.
         */
        private int flushInterval = 15;
        /**
         * Timeout of flush. In the timeunit of seconds.
         */
        private int flushTimeout = 10;
        /**
         * Concurrent consumer threads for batch writing.
         */
        private int concurrentWriteThreads = 2;
        /**
         * Max size of {@link org.apache.skywalking.oap.server.core.query.type.ProfileTask} to be fetched in a single
         * request.
         */
        private int profileTaskQueryMaxSize;

        /**
         * If the BanyanDB server is configured with TLS, config the TLS cert file path and open tls connection.
         */
        private String sslTrustCAPath = "";
        /**
         * Max size of {@link org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTask} to be fetched in a
         * single request.
         */
        private int asyncProfilerTaskQueryMaxSize;

        private int resultWindowMaxSize = 10000;
        private int metadataQueryMaxSize = 5000;
        private int segmentQueryMaxSize = 200;
        private int profileDataQueryBatchSize = 100;
    }

    // The configuration of the groups.
    // since 10.2.0

    @Getter
    @Setter
    public static class Stage {
        private StageName name;
        // Node selector specifying target nodes for this stage.
        // Optional; if provided, it must be a non-empty string.
        private String nodeSelector;
        private int shardNum;
        private int segmentInterval;
        private int ttl;
        // Indicates whether segments that are no longer live should be closed.
        private boolean close = false;
    }

    public enum StageName {
        hot,
        warm,
        cold;
    }

    @Getter
    @Setter
    public static class GroupResource {
        private int shardNum;
        private int segmentInterval;
        private int ttl;
        private boolean enableWarmStage = false;
        private boolean enableColdStage = false;
        private List<String> defaultQueryStages = new ArrayList<>(2);
        private List<Stage> additionalLifecycleStages = new ArrayList<>(2);

        public GroupResource() {
            defaultQueryStages.add(StageName.hot.name());
        }
    }

    //The group settings of records.
    /**
     * The RecordsNormal defines settings for datasets not specified in "super".
     * Each dataset will be grouped under a single group named "normal".
     */
    @Getter
    @Setter
    public static class RecordsNormal extends BanyanDBStorageConfig.GroupResource {
    }

    /**
     * RecordsSuper is a special dataset designed to store trace or log data that is too large for normal datasets.
     * Each super dataset will be a separate group in BanyanDB.
     */
    @Getter
    @Setter
    public static class RecordsSuper extends BanyanDBStorageConfig.GroupResource {
    }

    // The group settings of metrics.
    //
    // OAP stores metrics based its granularity.
    // Valid values are "day", "hour", and "minute". That means metrics will be stored in the three separate groups.
    // Non-"minute" are governed by the "core.downsampling" setting.
    // For example, if "core.downsampling" is set to "hour", the "hour" will be used, while "day" are ignored.

    /**
     * The MetricsMin defines settings for "minute" group metrics.
     */
    @Getter
    @Setter
    public static class MetricsMin extends BanyanDBStorageConfig.GroupResource {
    }

    /**
     * The MetricsHour defines settings for "hour" group metrics.
     */
    @Getter
    @Setter
    public static class MetricsHour extends BanyanDBStorageConfig.GroupResource {
    }

    /**
     * The MetricsDay defines settings for "day" group metrics.
     */
    @Getter
    @Setter
    public static class MetricsDay extends BanyanDBStorageConfig.GroupResource {
    }

    /**
     # If the metrics is marked as "index_mode", the metrics will be stored in the "index" group.
     # The "index" group is designed to store metrics that are used for indexing without value columns.
     # Such as `service_traffic`, `network_address_alias`, etc.
     # "index_mode" requires BanyanDB *0.8.0* or later.
     */
    @Getter
    @Setter
    public static class Metadata extends BanyanDBStorageConfig.GroupResource {
    }

    /**
     * The group settings of UI and profiling.
     */
    @Getter
    @Setter
    public static class Property extends BanyanDBStorageConfig.GroupResource {
    }
}
