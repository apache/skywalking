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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import org.apache.skywalking.library.elasticsearch.response.Mappings;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

public class IndexStructures {
    private final Map<String, Fields> mappingStructures;
    private final Map<String, UpdatableIndexSettings> indexSettingStructures;

    public IndexStructures() {
        this.mappingStructures = new HashMap<>();
        this.indexSettingStructures = new HashMap<>();
    }

    public Mappings getMapping(String tableName) {
        Map<String, Object> properties =
            mappingStructures.containsKey(tableName) ?
                mappingStructures.get(tableName).properties : new HashMap<>();
        Mappings.Source source =
                    mappingStructures.containsKey(tableName) ?
                        mappingStructures.get(tableName).source : new Mappings.Source();
        return Mappings.builder()
                       .type(ElasticSearchClient.TYPE)
                       .properties(properties)
                       .source(source)
                       .build();
    }

    /**
     * Add or append mapping/settings when the current structures don't contain the input structure or having
     * new fields in it.
     */
    public void putStructure(String tableName, Mappings mapping, Map<String, Object> settings) {
        if (CollectionUtils.isNotEmpty(settings) && Objects.nonNull(settings.get("index"))) {
            this.indexSettingStructures.putIfAbsent(tableName, new UpdatableIndexSettings(
                (Map<String, Object>) settings.get("index")));
        }
        if (Objects.isNull(mapping)
            || Objects.isNull(mapping.getProperties())
            || mapping.getProperties().isEmpty()) {
            return;
        }
        Fields fields = new Fields(mapping);
        if (mappingStructures.containsKey(tableName)) {
            mappingStructures.get(tableName).appendNewFields(fields);
        } else {
            mappingStructures.put(tableName, fields);
        }
    }

    /**
     * Returns mappings with fields that not exist in the input mappings.
     * The input mappings should be history mapping from current index.
     * Do not return _source config to avoid current index update conflict.
     */
    public Mappings diffMappings(String tableName, Mappings mappings) {
        if (!mappingStructures.containsKey(tableName)) {
            return new Mappings();
        }
        Map<String, Object> diffProperties =
            mappingStructures.get(tableName).diffFields(new Fields(mappings));
        return Mappings.builder()
                       .type(ElasticSearchClient.TYPE)
                       .properties(diffProperties)
                       .build();
    }

    /**
     * Returns true when the current structures already contains the properties of the input
     * mappings.
     */
    public boolean containsMapping(String tableName, Mappings mappings) {
        if (Objects.isNull(mappings) ||
            CollectionUtils.isEmpty(mappings.getProperties())) {
            return true;
        }

        return mappingStructures.containsKey(tableName)
            && mappingStructures.get(tableName)
                                .containsAllFields(new Fields(mappings));
    }

    /**
     * Returns true when the current index setting equals the input.
     */
    public boolean compareIndexSetting(String tableName, Map<String, Object> settings) {
        if ((CollectionUtils.isEmpty(settings) || CollectionUtils.isEmpty((Map) settings.get("index")))
            && Objects.isNull(indexSettingStructures.get(tableName))) {
            return true;
        }

        return indexSettingStructures.containsKey(tableName)
            && indexSettingStructures.get(tableName).
                                     equals(new UpdatableIndexSettings((Map<String, Object>) settings.get("index")));
    }

    /**
     * The mapping properties of the template or index.
     */
    public static class Fields {
        private final Map<String, Object> properties;
        private Mappings.Source source;

        private Fields(Mappings mapping) {
            this.properties = mapping.getProperties();
            this.source = mapping.getSource();
        }

        /**
         * Returns ture when the input fields have already been stored in the properties.
         */
        private boolean containsAllFields(Fields fields) {
            if (this.properties.size() < fields.properties.size()) {
                return false;
            }
            boolean isContains = fields.properties.entrySet().stream()
                    .allMatch(item -> Objects.equals(properties.get(item.getKey()), item.getValue()));
            if (!isContains) {
                return false;
            }
            return fields.source.getExcludes().containsAll(this.source.getExcludes());
        }

        /**
         * Append new fields and update.
         * Properties combine input and exist, update property's attribute, won't remove old one.
         * Source will be updated to the input.
         */
        private void appendNewFields(Fields fields) {
            properties.putAll(fields.properties);
            source = fields.source;
        }

        /**
         * Returns the properties that not exist in the input fields.
         */
        private Map<String, Object> diffFields(Fields fields) {
            return this.properties.entrySet().stream()
                                  .filter(e -> !fields.properties.containsKey(e.getKey()))
                                  .collect(Collectors.toMap(
                                      Map.Entry::getKey,
                                      Map.Entry::getValue
                                  ));
        }
    }

    /**
     * The index settings structure which only includes needs to compare for update fields
     */
    @EqualsAndHashCode
    public static class UpdatableIndexSettings {
        private final String replicas;
        private final String shards;

        public UpdatableIndexSettings(Map<String, Object> indexSettings) {
            this.replicas = String.valueOf(indexSettings.getOrDefault("number_of_replicas", Const.EMPTY_STRING));
            this.shards = String.valueOf(indexSettings.getOrDefault("number_of_shards", Const.EMPTY_STRING));
        }
    }
}
