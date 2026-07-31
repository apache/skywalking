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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.banyandb.model.v1.BanyandbModel;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

@Getter
@Setter
public class BanyanDBStorageConfig extends ModuleConfig {
    private Global global = new Global();
    private RecordsNormal recordsNormal = new RecordsNormal();
    private Trace trace = new Trace();
    private ZipkinTrace zipkinTrace = new ZipkinTrace();
    private RecordsTrace recordsTrace = new RecordsTrace();
    private RecordsZipkinTrace recordsZipkinTrace = new RecordsZipkinTrace();
    private RecordsLog recordsLog = new RecordsLog();
    private RecordsBrowserErrorLog recordsBrowserErrorLog = new RecordsBrowserErrorLog();

    private MetricsMin metricsMin = new MetricsMin();
    private MetricsHour metricsHour = new MetricsHour();
    private MetricsDay metricsDay = new MetricsDay();
    private Metadata metadata = new Metadata();
    private Property property = new Property();

    private Map<String/*metric name*/, Map<String, TopN>/*ruleName, topN*/> topNConfigs = new HashMap<>();

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

        private String user;
        private String password;
        /**
         * If the BanyanDB server is configured with TLS, config the TLS cert file path and open tls connection.
         */
        private String sslTrustCAPath = "";
        /**
         * Max size of {@link org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTask} to be fetched in a
         * single request.
         */
        private int asyncProfilerTaskQueryMaxSize;
        /**
         * Max size of {@link org.apache.skywalking.oap.server.core.query.type.PprofTask} to be fetched in a
         * single request.
         */
        private int pprofTaskQueryMaxSize;
        
        /**
         * Row cap for a query that does not carry a limit of its own — cache loads such as network aliases,
         * and every read whose result size is not bounded by paging. It is always sent to the server: a
         * BydbQL query with no {@code LIMIT} would otherwise fall back to BanyanDB's own default (100 rows
         * for measures, 20 for streams/traces), which truncates the result set silently.
         */
        private int resultWindowMaxSize = 10000;
        private int metadataQueryMaxSize = 5000;
        private int segmentQueryMaxSize = 200;
        private int profileDataQueryBatchSize = 100;
        private boolean cleanupUnusedTopNRules = true;
        private String namespace = "sw";
        private String compatibleServerApiVersions;

        public String[] getTargets() {
            return Iterables.toArray(
                Splitter.on(",").omitEmptyStrings().trimResults().split(this.targets), String.class);
        }

        public String[] getCompatibleServerApiVersions() {
            return Iterables.toArray(
                Splitter.on(",").omitEmptyStrings().trimResults().split(this.compatibleServerApiVersions), String.class);
        }
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
        private int replicas;
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
        private int replicas;
        private boolean enableWarmStage = false;
        private boolean enableColdStage = false;
        private List<String> defaultQueryStages = new ArrayList<>(2);
        private List<Stage> additionalLifecycleStages = new ArrayList<>(2);
        // The group-scoped trace retention pipeline. Only honored for CATALOG_TRACE groups.
        // Named distinctly from the "pipeline" YAML key so the reflective copyProperties pass
        // skips it (the nested block is parsed separately by copyPipeline).
        private TracePipeline tracePipeline = new TracePipeline();

        public GroupResource() {
            defaultQueryStages.add(StageName.hot.name());
        }
    }

    /**
     * TracePipeline mirrors BanyanDB's group-scoped {@code TracePipelineConfig}: an ordered chain of
     * sampler plugins that retain/drop traces in-merge (and at segment finalization).
     */
    @Getter
    @Setter
    public static class TracePipeline {
        // Whether the trace retention pipeline is active for the group.
        private boolean enabled = false;
        // Pipeline-wide events to run, e.g. PIPELINE_EVENT_MERGE, PIPELINE_EVENT_FINALIZE.
        private List<String> enabledEvents = new ArrayList<>(2);
        // Per-trace maturity window (seconds) for the in-merge filter; 0 uses the engine default.
        private int mergeGraceSeconds;
        // Per-segment settling window (seconds) for the finalization pass; 0 uses the engine default.
        private int finalizeGraceSeconds;
        // Ordered sampler plugin chain evaluated by the enabled events.
        private List<SamplerPluginConfig> plugins = new ArrayList<>(1);
    }

    /**
     * SamplerPluginConfig mirrors BanyanDB's {@code SamplerPlugin}: a native Go plugin (.so) that owns a
     * keep/drop verdict over a vectorized batch of traces.
     */
    @Getter
    @Setter
    public static class SamplerPluginConfig {
        // Operator-facing plugin identity within the pipeline chain.
        private String name;
        // The plugin .so filename, resolved within the data node's trusted plugin directory.
        private String path;
        // The constructor symbol the engine looks up; defaults to "NewSampler".
        private String symbol = "NewSampler";
        // The ABI version the plugin was built against.
        private int abiVersion = 1;
        // Free-form plugin configuration serialized to a protobuf Struct for the constructor.
        private Map<String, Object> config = new HashMap<>();
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

    // RecordsSuper is a special dataset designed to store traces or logs data that is too large for normal datasets.
    // Each super dataset will be a separate group in BanyanDB.

    @Getter
    @Setter
    public static class RecordsLog extends BanyanDBStorageConfig.GroupResource {
    }

    public static class Trace extends BanyanDBStorageConfig.GroupResource {
    }

    @Getter
    @Setter
    public static class ZipkinTrace extends BanyanDBStorageConfig.GroupResource {
    }

    @Getter
    @Setter
    public static class RecordsTrace extends BanyanDBStorageConfig.GroupResource {
    }

    @Getter
    @Setter
    public static class RecordsZipkinTrace extends BanyanDBStorageConfig.GroupResource {
    }

    @Getter
    @Setter
    public static class RecordsBrowserErrorLog extends BanyanDBStorageConfig.GroupResource {
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

    @Data
    public static class TopN {
        private String name;
        /**
         * The size of LRU determines the maximally tolerated time range.
         * The buffers in the time range are kept in the memory so that
         * the data in [T - lruSize * n, T] would be accepted in the pre-aggregation process.
         * T = the current time in the current dimensionality.
         * n = interval in the current dimensionality.
         * lruSizeMinute defines how many time_buckets are held in the memory for minute-level metrics.
         * For example, "10" means data points belonging to the latest "10" time_buckets will be persisted.
         */
        private int lruSizeMinute = 10;
        /**
         * lruSizeHourDay defines how many time_buckets are held in the memory for hour and day for minute-level metrics.
         * For example, "2" means data points belonging to the latest "2" time_buckets will be persisted.
         */
        private int lruSizeHourDay = 2;

        /**
         * counters_number defines max size of entries in a time window for the pre-aggregation.
         */
        private int countersNumber = 1000;

        /**
         * groupByTagNames defines the tags to be used for grouping the TopN results.
         * If not set, the default is empty, meaning no grouping.
         */
        private List<String> groupByTagNames;

        /**
         * sort defines the sorting order of the TopN results.
         * Default is "all", which means include `des and asc`.
         */
        private Sort sort = Sort.all;

        private Set<KeyValue> excludes = new HashSet<>();

        public enum Sort {
            all(BanyandbModel.Sort.SORT_UNSPECIFIED),
            des(BanyandbModel.Sort.SORT_DESC),
            asc(BanyandbModel.Sort.SORT_ASC);

            @Getter
            private final BanyandbModel.Sort banyandbSort;

            Sort(final BanyandbModel.Sort sort) {
                this.banyandbSort = sort;
            }
        }
    }
}
