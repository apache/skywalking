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

import com.google.common.base.Strings;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.status.ConfigDumpExtension;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageConfig.Global;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageConfig.GroupResource;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageConfig.Stage;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageConfig.TopN;

/**
 * Flattens the loaded, environment-resolved {@link BanyanDBStorageConfig} (from {@code bydb.yml}
 * and {@code bydb-topn.yml}) into {@code /debugging/config/dump} rows. Without this, a BanyanDB
 * deployment shows an empty {@code storage.banyandb} block in the dump, because that configuration
 * lives in a separate file the boot-time dump does not parse.
 *
 * <p>Keys read straight from the loaded POJO, never re-read from the file, so the values are the
 * effective post-environment-override ones. Group segment names mirror the {@code groups:} keys in
 * {@code bydb.yml} so the dump reads like the source file. Secret masking (e.g. {@code global.user}
 * / {@code global.password}) is applied centrally by the dump, so this extension returns raw values.
 *
 * @since 11.0.0
 */
public class BanyanDBConfigDumpExtension implements ConfigDumpExtension {
    private final String prefix;
    private final BanyanDBStorageConfig config;

    public BanyanDBConfigDumpExtension(final String prefix, final BanyanDBStorageConfig config) {
        this.prefix = prefix;
        this.config = config;
    }

    @Override
    public Map<String, String> dumpConfigurations() {
        final Map<String, String> dump = new LinkedHashMap<>();
        flattenGlobal(prefix + ".global", config.getGlobal(), dump);

        // Only the groups populated from bydb.yml are emitted; segment names match its `groups:` keys.
        final Map<String, GroupResource> groups = new LinkedHashMap<>();
        groups.put("records", config.getRecordsNormal());
        groups.put("recordsLog", config.getRecordsLog());
        groups.put("trace", config.getTrace());
        groups.put("zipkinTrace", config.getZipkinTrace());
        groups.put("recordsBrowserErrorLog", config.getRecordsBrowserErrorLog());
        groups.put("metricsMinute", config.getMetricsMin());
        groups.put("metricsHour", config.getMetricsHour());
        groups.put("metricsDay", config.getMetricsDay());
        groups.put("metadata", config.getMetadata());
        groups.put("property", config.getProperty());
        groups.forEach((name, group) -> flattenGroup(prefix + "." + name, group, dump));

        config.getTopNConfigs().forEach((metric, rules) ->
            rules.forEach((ruleName, topN) ->
                flattenTopN(prefix + ".topN." + metric + "." + ruleName, topN, dump)));
        return dump;
    }

    private void flattenGlobal(final String p, final Global g, final Map<String, String> dump) {
        // getTargets()/getCompatibleServerApiVersions() split the backing String into an array;
        // join them back so the dump shows the raw comma-separated form the operator configured.
        dump.put(p + ".targets", String.join(",", g.getTargets()));
        dump.put(p + ".maxBulkSize", String.valueOf(g.getMaxBulkSize()));
        dump.put(p + ".flushInterval", String.valueOf(g.getFlushInterval()));
        dump.put(p + ".flushTimeout", String.valueOf(g.getFlushTimeout()));
        dump.put(p + ".concurrentWriteThreads", String.valueOf(g.getConcurrentWriteThreads()));
        dump.put(p + ".profileTaskQueryMaxSize", String.valueOf(g.getProfileTaskQueryMaxSize()));
        dump.put(p + ".asyncProfilerTaskQueryMaxSize", String.valueOf(g.getAsyncProfilerTaskQueryMaxSize()));
        dump.put(p + ".pprofTaskQueryMaxSize", String.valueOf(g.getPprofTaskQueryMaxSize()));
        dump.put(p + ".resultWindowMaxSize", String.valueOf(g.getResultWindowMaxSize()));
        dump.put(p + ".metadataQueryMaxSize", String.valueOf(g.getMetadataQueryMaxSize()));
        dump.put(p + ".segmentQueryMaxSize", String.valueOf(g.getSegmentQueryMaxSize()));
        dump.put(p + ".profileDataQueryBatchSize", String.valueOf(g.getProfileDataQueryBatchSize()));
        dump.put(p + ".cleanupUnusedTopNRules", String.valueOf(g.isCleanupUnusedTopNRules()));
        dump.put(p + ".namespace", Strings.nullToEmpty(g.getNamespace()));
        dump.put(p + ".compatibleServerApiVersions", String.join(",", g.getCompatibleServerApiVersions()));
        dump.put(p + ".user", Strings.nullToEmpty(g.getUser()));
        dump.put(p + ".password", Strings.nullToEmpty(g.getPassword()));
        dump.put(p + ".sslTrustCAPath", Strings.nullToEmpty(g.getSslTrustCAPath()));
    }

    private void flattenGroup(final String p, final GroupResource group, final Map<String, String> dump) {
        dump.put(p + ".shardNum", String.valueOf(group.getShardNum()));
        dump.put(p + ".segmentInterval", String.valueOf(group.getSegmentInterval()));
        dump.put(p + ".ttl", String.valueOf(group.getTtl()));
        dump.put(p + ".replicas", String.valueOf(group.getReplicas()));
        dump.put(p + ".enableWarmStage", String.valueOf(group.isEnableWarmStage()));
        dump.put(p + ".enableColdStage", String.valueOf(group.isEnableColdStage()));
        dump.put(p + ".defaultQueryStages", group.getDefaultQueryStages().toString());
        final List<Stage> stages = group.getAdditionalLifecycleStages();
        for (int i = 0; i < stages.size(); i++) {
            flattenStage(p + ".additionalLifecycleStages." + i, stages.get(i), dump);
        }
    }

    private void flattenStage(final String p, final Stage stage, final Map<String, String> dump) {
        dump.put(p + ".name", stage.getName() == null ? "" : stage.getName().name());
        dump.put(p + ".nodeSelector", Strings.nullToEmpty(stage.getNodeSelector()));
        dump.put(p + ".shardNum", String.valueOf(stage.getShardNum()));
        dump.put(p + ".segmentInterval", String.valueOf(stage.getSegmentInterval()));
        dump.put(p + ".ttl", String.valueOf(stage.getTtl()));
        dump.put(p + ".replicas", String.valueOf(stage.getReplicas()));
        dump.put(p + ".close", String.valueOf(stage.isClose()));
    }

    private void flattenTopN(final String p, final TopN topN, final Map<String, String> dump) {
        dump.put(p + ".lruSizeMinute", String.valueOf(topN.getLruSizeMinute()));
        dump.put(p + ".lruSizeHourDay", String.valueOf(topN.getLruSizeHourDay()));
        dump.put(p + ".countersNumber", String.valueOf(topN.getCountersNumber()));
        dump.put(p + ".sort", topN.getSort() == null ? "" : topN.getSort().name());
        dump.put(p + ".groupByTagNames",
            topN.getGroupByTagNames() == null ? "[]" : topN.getGroupByTagNames().toString());
        dump.put(p + ".excludes", topN.getExcludes().stream()
            .map(kv -> kv.getKey() + "=" + kv.getValue())
            .collect(Collectors.joining(",", "[", "]")));
    }
}
