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

package org.apache.skywalking.oap.server.fetcher.cilium;

import io.cilium.api.flow.Endpoint;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ExcludeRules {

    private final Set<String> namespaces;
    private final List<Labels> labels;

    public static ExcludeRules loadRules(final String path) throws IOException {
        try (FileReader r = new FileReader(ResourceUtils.getPath(path).toFile())) {
            final RuleYaml yaml = new Yaml().loadAs(r, RuleYaml.class);
            return new ExcludeRules(yaml);
        }
    }

    /**
     * check if the endpoint should be excluded
     */
    public boolean shouldExclude(Endpoint endpoint) {
        // if the namespace is in the exclude list, return true
        if (namespaces.contains(endpoint.getNamespace())) {
            return true;
        }
        // if the endpoint has no labels, return false
        if (endpoint.getLabelsCount() == 0) {
            return false;
        }
        return labels.stream().anyMatch(label -> label.isMatch(endpoint));
    }

    private ExcludeRules(RuleYaml yaml) {
        this.namespaces = Set.copyOf(yaml.getNamespaces());
        this.labels = yaml.getLabels().stream().map(Labels::new).collect(Collectors.toList());
    }

    private static class Labels {
        private Map<String, String> labelMap;

        public Labels(Map<String, String> labelMap) {
            this.labelMap = labelMap;
        }

        /**
         * validate if the endpoint matches all the labels
         */
        public boolean isMatch(Endpoint endpoint) {
            int matchCount = 0;
            for (Map.Entry<String, String> entry : labelMap.entrySet()) {
                for (String endpointLabel : endpoint.getLabelsList()) {
                    // ignore when the key is not match
                    if (endpointLabel.indexOf(entry.getKey()) != 0) {
                        continue;
                    }
                    // ignore when the value is not match
                    if (!StringUtils.substring(endpointLabel, entry.getKey().length() + 1).equals(entry.getValue())) {
                        return false;
                    }
                    matchCount++;

                    // check the match count(full matched) to avoid unnecessary iteration
                    if (matchCount == labelMap.size()) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    @Data
    public static class RuleYaml {
        private List<String> namespaces;
        private List<Map<String, String>> labels;
    }
}
