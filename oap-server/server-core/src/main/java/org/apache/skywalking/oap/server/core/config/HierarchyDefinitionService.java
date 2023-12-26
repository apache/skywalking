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

package org.apache.skywalking.oap.server.core.config;

import java.io.FileNotFoundException;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.yaml.snakeyaml.Yaml;

@Slf4j
public class HierarchyDefinitionService implements Service {
    @Getter
    private Map<String, List<String>> hierarchyDefinition;

    public HierarchyDefinitionService(CoreModuleConfig moduleConfig) {
        this.hierarchyDefinition = new HashMap<>();
        if (moduleConfig.isEnableHierarchy()) {
            this.init();
            this.checkLayers();
        }
    }

    private void init() {
        try {
            Reader applicationReader = ResourceUtils.read("hierarchy-definition.yml");
            Yaml yaml = new Yaml();
            this.hierarchyDefinition = yaml.loadAs(applicationReader, Map.class);
        } catch (FileNotFoundException e) {
            throw new UnexpectedException("hierarchy-definition.yml not found.", e);
        }
    }

    private void checkLayers() {
        this.hierarchyDefinition.forEach((layer, lowerLayers) -> {
            if (lowerLayers.contains(layer)) {
                throw new IllegalArgumentException(
                    "hierarchy-definition.yml " + layer + " contains recursive hierarchy relation.");
            }
            checkRecursive(layer);
        });
    }

    private void checkRecursive(String layerName) {
        try {
            List<String> lowerLayers = this.hierarchyDefinition.get(layerName);
            if (lowerLayers == null) {
                return;
            }
            lowerLayers.forEach(this::checkRecursive);
        } catch (Throwable e) {
            throw new IllegalArgumentException(
                "hierarchy-definition.yml " + layerName + " contains recursive hierarchy relation.");
        }
    }
}
