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
import lombok.Getter;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;

public class IndexStructures {
    private final Map<String, Fields> structures;
    @Getter
    private final PropertiesExtractor extractor;
    @Getter
    private final PropertiesWrapper wrapper;

    public IndexStructures() {
        this.structures = new HashMap<>();
        this.extractor = doGetPropertiesExtractor();
        this.wrapper = doGetPropertiesWrapper();
    }

    protected PropertiesExtractor doGetPropertiesExtractor() {
        return mapping -> (Map<String, Object>) ((Map<String, Object>) mapping.get(
            ElasticSearchClient.TYPE)).get("properties");
    }

    protected PropertiesWrapper doGetPropertiesWrapper() {
        return properties -> {
            HashMap<String, Object> mappings = new HashMap<>();
            HashMap<String, Object> types = new HashMap<>();
            mappings.put(ElasticSearchClient.TYPE, types);
            types.put("properties", properties);
            return mappings;
        };
    }

    public Map<String, Object> getMapping(String tableName) {
        return wrapper.wrapper(
            structures.containsKey(tableName) ? structures.get(tableName).properties : new HashMap<>());
    }

    /**
     * Add or append field when the current structures don't contain the input structure or having new fields in it.
     */
    public void putStructure(String tableName, Map<String, Object> mapping) {
        if (Objects.isNull(mapping) || mapping.isEmpty()) {
            return;
        }
        Map<String, Object> properties = this.extractor.extract(mapping);
        Fields fields = new Fields(properties);
        if (structures.containsKey(tableName)) {
            structures.get(tableName).appendNewFields(fields);
        } else {
            structures.put(tableName, fields);
        }
    }

    /**
     * Returns mappings with fields that not exist in the input mappings.
     */
    public Map<String, Object> diffStructure(String tableName, Map<String, Object> mappings) {
        if (!structures.containsKey(tableName)) {
            return new HashMap<>();
        }
        Map<String, Object> properties = this.extractor.extract(mappings);
        Map<String, Object> diffProperties = structures.get(tableName).diffFields(new Fields(properties));
        return this.wrapper.wrapper(diffProperties);
    }

    /**
     * Returns true when the current structures already contains the properties of the input mappings.
     */
    public boolean containsStructure(String tableName, Map<String, Object> mappings) {
        if (Objects.isNull(mappings) || mappings.isEmpty()) {
            return true;
        }
        return structures.containsKey(tableName)
            && structures.get(tableName).containsAllFields(new Fields(this.extractor.extract(mappings)));
    }

    /**
     * The properties of the template or index.
     */
    public static class Fields {
        private final Map<String, Object> properties;

        private Fields(Map<String, Object> properties) {
            this.properties = properties;
        }

        /**
         * Returns ture when the input fields have already been stored in the properties.
         */
        private boolean containsAllFields(Fields fields) {
            return fields.properties.entrySet().stream().allMatch(item -> this.properties.containsKey(item.getKey()));
        }

        /**
         * Append new fields to the properties when have new fields.
         */
        private void appendNewFields(Fields fields) {
            Map<String, Object> newFields = fields.properties.entrySet()
                                                             .stream()
                                                             .filter(e -> !this.properties.containsKey(e.getKey()))
                                                             .collect(Collectors.toMap(
                                                                 Map.Entry::getKey, Map.Entry::getValue
                                                             ));
            newFields.forEach(properties::put);
        }

        /**
         * Returns the properties that not exist in the input fields.
         */
        private Map<String, Object> diffFields(Fields fields) {
            return this.properties.entrySet().stream()
                                  .filter(e -> !fields.properties.containsKey(e.getKey()))
                                  .collect(Collectors.toMap(
                                      Map.Entry::getKey, Map.Entry::getValue
                                  ));
        }
    }

    /**
     * Extract properties form the mappings.
     */
    @FunctionalInterface
    public interface PropertiesExtractor {
        Map<String, Object> extract(Map<String, Object> mappings);
    }

    /**
     * Wrapper properties to the mappings.
     */
    @FunctionalInterface
    public interface PropertiesWrapper {
        Map<String, Object> wrapper(Map<String, Object> properties);
    }
}
