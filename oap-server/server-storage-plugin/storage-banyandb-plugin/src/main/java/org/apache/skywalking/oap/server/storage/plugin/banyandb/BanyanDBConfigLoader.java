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

import java.io.FileNotFoundException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.util.PropertyPlaceholderHelper;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageConfig.TopN;
import org.yaml.snakeyaml.Yaml;

import static org.apache.skywalking.oap.server.library.util.YamlConfigLoaderUtils.convertValueString;
import static org.apache.skywalking.oap.server.library.util.YamlConfigLoaderUtils.copyProperties;
import static org.apache.skywalking.oap.server.library.util.YamlConfigLoaderUtils.replacePropertyAndLog;

@Slf4j
public class BanyanDBConfigLoader {
    private final ModuleProvider moduleProvider;
    private final BanyanDBStorageConfig config;
    private final Yaml yaml;

    public BanyanDBConfigLoader(final ModuleProvider moduleProvider) {
        this.moduleProvider = moduleProvider;
        this.config = new BanyanDBStorageConfig();
        this.yaml = new Yaml();
    }

    public BanyanDBStorageConfig loadConfig() throws ModuleStartException {
         loadBaseConfig();
         loadTopNConfig();
         return config;
    }

    private void loadBaseConfig() throws ModuleStartException {
        Reader applicationReader;
        try {
            applicationReader = ResourceUtils.read("bydb.yml");
        } catch (FileNotFoundException e) {
            throw new ModuleStartException("Cannot find the BanyanDB configuration file [bydb.yml].", e);
        }
        Map<String, Map<String, ?>> configMap = yaml.loadAs(applicationReader, Map.class);
        if (configMap == null) {
            return;
        }

        Map<String, Properties> configProperties = new HashMap<>();
        configMap.forEach((part, c) -> {
            if (c != null) {
                final Properties properties = parseConfig(c);
                configProperties.put(part, properties);
            }
        });

        try {
            copyProperties(
                config.getGlobal(), configProperties.get("global"), moduleProvider.getModule().name(),
                moduleProvider.name()
            );
            Properties groups = configProperties.get("groups");
            Properties recordsNormal = (Properties) groups.get(BanyanDB.StreamGroup.RECORDS.getName());
            copyProperties(
                config.getRecordsNormal(), recordsNormal,
                moduleProvider.getModule().name(), moduleProvider.name()
            );
            copyStages(recordsNormal, config.getRecordsNormal());

            Properties log = (Properties) groups.get(BanyanDB.StreamGroup.RECORDS_LOG.getName());
            copyProperties(
                    config.getRecordsLog(), log,
                    moduleProvider.getModule().name(), moduleProvider.name()
            );
            copyStages(log, config.getRecordsLog());

            Properties segment = (Properties) groups.get(BanyanDB.TraceGroup.TRACE.getName());
            copyProperties(
                config.getTrace(), segment,
                moduleProvider.getModule().name(), moduleProvider.name()
            );
            copyStages(segment, config.getTrace());
            // Read the pipeline from the raw YAML map, not the flattened Properties: parseConfig
            // stringifies nested lists via replacePropertyAndLog, which corrupts the plugins block.
            Map<String, ?> rawGroups = configMap.get("groups");
            if (rawGroups != null && rawGroups.get(BanyanDB.TraceGroup.TRACE.getName()) instanceof Map) {
                copyPipeline((Map<String, Object>) rawGroups.get(BanyanDB.TraceGroup.TRACE.getName()), config.getTrace());
            }

            Properties zipkinSpan = (Properties) groups.get(BanyanDB.TraceGroup.ZIPKIN_TRACE.getName());
            copyProperties(
                config.getZipkinTrace(), zipkinSpan,
                moduleProvider.getModule().name(), moduleProvider.name()
            );
            copyStages(zipkinSpan, config.getZipkinTrace());
            if (rawGroups != null && rawGroups.get(BanyanDB.TraceGroup.ZIPKIN_TRACE.getName()) instanceof Map) {
                copyPipeline(
                    (Map<String, Object>) rawGroups.get(BanyanDB.TraceGroup.ZIPKIN_TRACE.getName()), config.getZipkinTrace());
            }

            Properties aiAgent = (Properties) groups.get(BanyanDB.StreamGroup.RECORDS_AI_AGENT.getName());
            copyProperties(
                    config.getRecordsAIAgent(), aiAgent,
                    moduleProvider.getModule().name(), moduleProvider.name()
            );
            copyStages(aiAgent, config.getRecordsAIAgent());

            Properties browserErrorLog = (Properties) groups.get(BanyanDB.StreamGroup.RECORDS_BROWSER_ERROR_LOG.getName());
            copyProperties(
                config.getRecordsBrowserErrorLog(), browserErrorLog,
                moduleProvider.getModule().name(), moduleProvider.name()
            );
            copyStages(browserErrorLog, config.getRecordsBrowserErrorLog());

            Properties metricsMin = (Properties) groups.get(BanyanDB.MeasureGroup.METRICS_MINUTE.getName());
            copyProperties(
                config.getMetricsMin(), metricsMin,
                moduleProvider.getModule().name(), moduleProvider.name()
            );
            copyStages(metricsMin, config.getMetricsMin());

            Properties metricsHour = (Properties) groups.get(BanyanDB.MeasureGroup.METRICS_HOUR.getName());
            copyProperties(
                config.getMetricsHour(), metricsHour,
                moduleProvider.getModule().name(), moduleProvider.name()
            );
            copyStages(metricsHour, config.getMetricsHour());

            Properties metricsDay = (Properties) groups.get(BanyanDB.MeasureGroup.METRICS_DAY.getName());
            copyProperties(
                config.getMetricsDay(), metricsDay,
                moduleProvider.getModule().name(), moduleProvider.name()
            );
            copyStages(metricsDay, config.getMetricsDay());

            copyProperties(
                config.getMetadata(), (Properties) groups.get(BanyanDB.MeasureGroup.METADATA.getName()),
                moduleProvider.getModule().name(), moduleProvider.name()
            );
            copyProperties(
                config.getProperty(), (Properties) groups.get(BanyanDB.PropertyGroup.PROPERTY.getName()),
                moduleProvider.getModule().name(), moduleProvider.name()
            );
        } catch (IllegalAccessException e) {
            throw new ModuleStartException("Failed to load BanyanDB configuration.", e);
        }
    }

    private Properties parseConfig(final Map<String, ?> config) {
        final Properties properties = new Properties();
        for (Map.Entry<String, ?> entry : config.entrySet()) {
            String propertyName = entry.getKey();
            Object propertyValue = entry.getValue();
            // A blank YAML value ("key:" with nothing after it) parses to null, and Properties is
            // Hashtable-backed so putting a null throws. Skip it instead: the key is simply left
            // unset, which copyProperties then leaves at the field's default — the same outcome as
            // omitting the line, rather than failing OAP startup with an opaque NPE.
            if (propertyValue == null) {
                continue;
            }
            if (propertyValue instanceof Map) {
                Properties subProperties = parseConfig((Map<String, ?>) propertyValue);
                properties.put(propertyName, subProperties);
            } else {
                properties.put(propertyName, propertyValue);
                replacePropertyAndLog(
                    propertyName, propertyValue, properties, this.moduleProvider.name(), yaml);
            }
        }
        return properties;
    }

    private void copyStages(final Properties group,
                            final BanyanDBStorageConfig.GroupResource groupResource) throws IllegalAccessException {
        if (groupResource.isEnableWarmStage()) {
            BanyanDBStorageConfig.Stage warm = new BanyanDBStorageConfig.Stage();
            warm.setName(BanyanDBStorageConfig.StageName.warm);
            copyProperties(
                warm, (Properties) group.get(BanyanDBStorageConfig.StageName.warm.name()),
                moduleProvider.getModule().name(), moduleProvider.name()
            );
            groupResource.getAdditionalLifecycleStages().add(warm);
            groupResource.getDefaultQueryStages().add(BanyanDBStorageConfig.StageName.warm.name());
        }

        if (groupResource.isEnableColdStage()) {
            BanyanDBStorageConfig.Stage cold = new BanyanDBStorageConfig.Stage();
            cold.setName(BanyanDBStorageConfig.StageName.cold);
            cold.setClose(true);
            copyProperties(
                cold, (Properties) group.get(BanyanDBStorageConfig.StageName.cold.name()),
                moduleProvider.getModule().name(), moduleProvider.name()
            );
            groupResource.getAdditionalLifecycleStages().add(cold);
        }
    }

    @SuppressWarnings("unchecked")
    private void copyPipeline(final Map<String, Object> traceGroup,
                             final BanyanDBStorageConfig.GroupResource groupResource) {
        if (traceGroup == null || !(traceGroup.get("pipeline") instanceof Map)) {
            return;
        }
        Map<String, Object> pipelineMap = (Map<String, Object>) traceGroup.get("pipeline");
        BanyanDBStorageConfig.TracePipeline pipeline = new BanyanDBStorageConfig.TracePipeline();
        Object enabled = resolveValue(pipelineMap.get("enabled"));
        if (enabled != null) {
            pipeline.setEnabled(Boolean.parseBoolean(enabled.toString()));
        }
        Object mergeGrace = resolveValue(pipelineMap.get("mergeGraceSeconds"));
        if (mergeGrace != null) {
            pipeline.setMergeGraceSeconds(Integer.parseInt(mergeGrace.toString()));
        }
        Object finalizeGrace = resolveValue(pipelineMap.get("finalizeGraceSeconds"));
        if (finalizeGrace != null) {
            pipeline.setFinalizeGraceSeconds(Integer.parseInt(finalizeGrace.toString()));
        }
        addEnabledEvents(pipeline, pipelineMap.get("enabledEvents"));
        if (pipelineMap.get("plugins") instanceof List) {
            for (Object pluginObj : (List<Object>) pipelineMap.get("plugins")) {
                if (!(pluginObj instanceof Map)) {
                    continue;
                }
                Map<String, Object> pluginMap = (Map<String, Object>) pluginObj;
                BanyanDBStorageConfig.SamplerPluginConfig plugin = new BanyanDBStorageConfig.SamplerPluginConfig();
                Object name = resolveValue(pluginMap.get("name"));
                if (name != null) {
                    plugin.setName(name.toString());
                }
                Object path = resolveValue(pluginMap.get("path"));
                if (path != null) {
                    plugin.setPath(path.toString());
                }
                Object symbol = resolveValue(pluginMap.get("symbol"));
                if (symbol != null) {
                    plugin.setSymbol(symbol.toString());
                }
                Object abiVersion = resolveValue(pluginMap.get("abiVersion"));
                if (abiVersion != null) {
                    plugin.setAbiVersion(Integer.parseInt(abiVersion.toString()));
                }
                if (pluginMap.get("config") instanceof Map) {
                    Map<String, Object> resolvedConfig = new HashMap<>();
                    for (Map.Entry<String, Object> entry : ((Map<String, Object>) pluginMap.get("config")).entrySet()) {
                        resolvedConfig.put(entry.getKey(), resolveConfigValue(entry.getValue()));
                    }
                    plugin.setConfig(resolvedConfig);
                }
                pipeline.getPlugins().add(plugin);
            }
        }
        groupResource.setTracePipeline(pipeline);
    }

    /**
     * Adds the pipeline's enabled events, accepting either shape the YAML can produce.
     *
     * <p>A block list stays a {@link List}, but an <code>${ENV:default}</code> placeholder is a
     * scalar, so it arrives as a comma-separated String — the form an environment override has to
     * take. Handling only the List silently left the events empty, and the data node reads an
     * empty list as MERGE for backward compatibility, so an operator asking for FINALIZE would
     * have got MERGE instead with no error anywhere.
     *
     * @param pipeline the pipeline config to populate.
     * @param raw      the <code>enabledEvents</code> value as parsed from YAML, possibly null.
     */
    private void addEnabledEvents(final BanyanDBStorageConfig.TracePipeline pipeline, final Object raw) {
        List<Object> rawEvents = new ArrayList<>();
        if (raw instanceof List) {
            rawEvents.addAll((List<Object>) raw);
        } else if (raw != null) {
            Object resolved = resolveValue(raw);
            if (resolved != null) {
                for (String event : resolved.toString().split(",")) {
                    rawEvents.add(event);
                }
            }
        }
        for (Object event : rawEvents) {
            Object resolved = resolveValue(event);
            if (resolved == null) {
                continue;
            }
            String name = resolved.toString().trim();
            // A blank entry is what an operator writing `${SW_..._EVENTS:}` produces; adding it
            // would fail later at PipelineEvent.valueOf with a message naming an empty string.
            if (!name.isEmpty()) {
                pipeline.getEnabledEvents().add(name);
            }
        }
    }

    // resolveConfigValue resolves ${ENV:default} placeholders in the scalar leaves of a plugin
    // config value while preserving its nested List/Map structure (e.g. keepTagRules is a list
    // of maps). resolveValue alone would stringify a List/Map via String.valueOf, corrupting the
    // nested shape before it reaches the protobuf Struct handed to the sampler.
    @SuppressWarnings("unchecked")
    private Object resolveConfigValue(final Object raw) {
        if (raw instanceof Map) {
            Map<String, Object> resolved = new HashMap<>();
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) raw).entrySet()) {
                resolved.put(entry.getKey(), resolveConfigValue(entry.getValue()));
            }
            return resolved;
        }
        if (raw instanceof List) {
            List<Object> resolved = new ArrayList<>();
            for (Object element : (List<Object>) raw) {
                resolved.add(resolveConfigValue(element));
            }
            return resolved;
        }
        // Non-String scalars (Number/Boolean) are already typed by the YAML parser; preserve them
        // as-is so a float such as healthySampleRate stays a number in the protobuf Struct
        // (convertValueString, used by resolveValue, coerces a Double back to String). Only a
        // String may carry a ${ENV:default} placeholder that resolveValue must substitute.
        if (raw instanceof String) {
            return resolveValue(raw);
        }
        return raw;
    }

    // resolveValue substitutes ${ENV:default} placeholders and coerces the result to its natural type
    // (Integer/Boolean/String), mirroring how flat scalar properties are handled in parseConfig.
    private Object resolveValue(final Object raw) {
        if (raw == null) {
            return null;
        }
        String resolved = PropertyPlaceholderHelper.INSTANCE.replacePlaceholders(String.valueOf(raw), new Properties());
        return convertValueString(resolved, yaml);
    }

    private void loadTopNConfig() throws ModuleStartException {
        Reader applicationReader;
        try {
            applicationReader = ResourceUtils.read("bydb-topn.yml");
        } catch (FileNotFoundException e) {
            throw new ModuleStartException("Cannot find the BanyanDB topN configuration file [bydb-topn.yml].", e);
        }
        Map<String, List<Map<String, ?>>> configMap = new Yaml().loadAs(applicationReader, Map.class);
        if (configMap == null) {
            return;
        }
        List<Map<String, ?>> topNConfig = configMap.get("TopN-Rules");
        if (topNConfig == null) {
            return;
        }
        for (Map<String, ?> rule : topNConfig) {
            TopN topN = new TopN();
            var name = rule.get("name");
            if (name == null) {
                throw new ModuleStartException("TopN rule name is missing in file [bydb-topn.yml].");
            }
            var metricName = rule.get("metricName");
            if (metricName == null) {
                throw new ModuleStartException("TopN rule metricName is missing in file [bydb-topn.yml].");
            }
            topN.setName(name.toString());
            var groupByTagNames = rule.get("groupByTagNames");
            if (groupByTagNames != null) {
                topN.setGroupByTagNames((List<String>) groupByTagNames);
            }
            var countersNumber = rule.get("countersNumber");
            if (countersNumber != null) {
                topN.setLruSizeMinute((int) countersNumber);
            }
            var lruSizeMinute = rule.get("lruSizeMinute");
            if (lruSizeMinute != null) {
                topN.setLruSizeMinute((int) lruSizeMinute);
            }
            var lruSizeHourDay = rule.get("lruSizeHourDay");
            if (lruSizeHourDay != null) {
                topN.setLruSizeMinute((int) lruSizeHourDay);
            }
            var sort = rule.get("sort");
            if (sort != null) {
                topN.setSort(TopN.Sort.valueOf(sort.toString()));
            }
            var excludes = rule.get("excludes");
            if (excludes != null) {
                for (Map<String, String> tag : (List<Map<String, String>>) excludes) {
                    var tagName = tag.get("tag");
                    var tagValue = tag.get("value");
                    if (tagName == null || tagValue == null) {
                        throw new ModuleStartException(
                            "TopN rule name: " + name + ", [tag] or [value] is missing in [excludes] item in file [bydb-topn.yml].");
                    }
                    topN.getExcludes().add(new KeyValue(tag.get("tag"), tag.get("value")));
                }
            }

            Map<String, TopN> map = config.getTopNConfigs().computeIfAbsent(metricName.toString(), k -> new HashMap<>());
            if (map.put(name.toString(), topN) != null) {
                throw new ModuleStartException("Duplicate TopN rule name: " + name + " in file [bydb-topn.yml].");
            }
        }
    }
}
