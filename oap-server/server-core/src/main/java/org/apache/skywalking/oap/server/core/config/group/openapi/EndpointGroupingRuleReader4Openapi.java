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

package org.apache.skywalking.oap.server.core.config.group.openapi;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public class EndpointGroupingRuleReader4Openapi {
    private final Map<String, /*serviceName*/ List<Map>/*openapiData*/> serviceOpenapiDefMap;
    private final static String DEFAULT_ENDPOINT_NAME_FORMAT = "${METHOD}:${PATH}";
    private final static String DEFAULT_ENDPOINT_NAME_MATCH_RULE = "${METHOD}:${PATH}";
    private final Map<String, String> requestMethodsMap = new HashMap<String, String>() {
        {
            put("get", "GET");
            put("post", "POST");
            put("put", "PUT");
            put("delete", "DELETE");
            put("trace", "TRACE");
            put("options", "OPTIONS");
            put("head", "HEAD");
            put("patch", "PATCH");
        }
    };

    public EndpointGroupingRuleReader4Openapi(final String openapiDefPath) throws FileNotFoundException {
        this.serviceOpenapiDefMap = this.parseFromDir(openapiDefPath);
    }

    public EndpointGroupingRuleReader4Openapi(final Map<String, String> openapiDefsConf) {
        this.serviceOpenapiDefMap = this.parseFromDynamicConf(openapiDefsConf);
    }

    public EndpointGroupingRule4Openapi read() {
        EndpointGroupingRule4Openapi endpointGroupingRule = new EndpointGroupingRule4Openapi();
        serviceOpenapiDefMap.forEach((serviceName, openapiDefs) -> {
            openapiDefs.forEach(openapiData -> {
                LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap>> paths =
                    (LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap>>) openapiData.get(
                        "paths");
                if (paths != null) {
                    paths.forEach((pathString, pathItem) -> {
                        pathItem.keySet().forEach(key -> {
                            String requestMethod = requestMethodsMap.get(key);
                            if (!StringUtil.isEmpty(requestMethod)) {
                                String endpointGroupName = formatEndPointName(
                                    pathString, requestMethod, openapiData);
                                String groupRegex = getGroupRegex(
                                    pathString, requestMethod, openapiData);
                                if (isTemplatePath(pathString)) {
                                    endpointGroupingRule.addGroupedRule(
                                        serviceName, endpointGroupName, groupRegex);
                                } else {
                                    endpointGroupingRule.addDirectLookup(
                                        serviceName, groupRegex, endpointGroupName);
                                }
                            }
                        });
                    });
                }
            });
        });
        endpointGroupingRule.sortRulesAll();
        return endpointGroupingRule;
    }

    private Map<String, List<Map>> parseFromDir(String openapiDefPath) throws FileNotFoundException {
        Map<String, List<Map>> serviceOpenapiDefMap = new HashMap<>();
        List<File> fileList = ResourceUtils.getDirectoryFilesRecursive(openapiDefPath, 1);
        for (File file : fileList) {
            if (!file.getName().endsWith(".yaml")) {
                continue;
            }
            Reader reader = new FileReader(file);
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Map openapiData = yaml.load(reader);
            if (openapiData != null) {
                serviceOpenapiDefMap.computeIfAbsent(getServiceName(openapiDefPath, file, openapiData), k -> new ArrayList<>()).add(openapiData);
            }
        }

        return serviceOpenapiDefMap;
    }

    private Map<String, List<Map>> parseFromDynamicConf(final Map<String, String> openapiDefsConf) {
        Map<String, List<Map>> serviceOpenapiDefMap = new HashMap<>();
        openapiDefsConf.forEach((itemName, openapiDefs) -> {
            String serviceName = itemName;
            //service map to multiple openapiDefs
            String[] itemNameInfo = itemName.split("\\.");
            if (itemNameInfo.length > 1) {
                serviceName = itemNameInfo[0];
            }
            Reader reader = new StringReader(openapiDefs);
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Map openapiData = yaml.load(reader);
            if (openapiData != null) {
                serviceOpenapiDefMap.computeIfAbsent(getServiceName(serviceName, openapiData), k -> new ArrayList<>())
                                    .add(openapiData);
            }
        });

        return serviceOpenapiDefMap;
    }

    private String getServiceName(String openapiDefPath, File file, Map openapiData) {
        String serviceName = (String) openapiData.get("x-sw-service-name");
        if (StringUtil.isEmpty(serviceName)) {
            File directory = new File(file.getParent());
            if (openapiDefPath.equals(directory.getName())) {
                throw new IllegalArgumentException(
                    "OpenAPI definition file: " + file.getAbsolutePath() + " found in root directory, but doesn't include x-sw-service-name extensive definition in the file.");
            }
            serviceName = directory.getName();
        }

        return serviceName;
    }

    private String getServiceName(String defaultServiceName, Map openapiData) {
        String serviceName = (String) openapiData.get("x-sw-service-name");
        if (StringUtil.isEmpty(serviceName)) {
            serviceName = defaultServiceName;
        }

        return serviceName;
    }

    private boolean isTemplatePath(String pathString) {
        return pathString.matches("(.*)\\{(.+?)}(.*)");
    }

    private String getGroupRegex(String pathString, String requstMathod, Map openapiData) {
        String endPointNameMatchRuleTemplate = (String) openapiData.get("x-sw-endpoint-name-match-rule");
        String endPointNameMatchRule = replaceTemplateVars(DEFAULT_ENDPOINT_NAME_MATCH_RULE, pathString, requstMathod);

        if (!StringUtil.isEmpty(endPointNameMatchRuleTemplate)) {
            endPointNameMatchRule = replaceTemplateVars(endPointNameMatchRuleTemplate, pathString, requstMathod);
        }

        if (isTemplatePath(endPointNameMatchRule)) {
            return endPointNameMatchRule.replaceAll("\\{(.+?)}", "([^/]+)");
        }
        return endPointNameMatchRule;
    }

    private String formatEndPointName(String pathString, String requstMethod, Map openapiData) {
        String endPointNameFormat = (String) openapiData.get("x-sw-endpoint-name-format");

        if (!StringUtil.isEmpty(endPointNameFormat)) {
            return replaceTemplateVars(endPointNameFormat, pathString, requstMethod);
        }

        return replaceTemplateVars(DEFAULT_ENDPOINT_NAME_FORMAT, pathString, requstMethod);
    }

    private String replaceTemplateVars(String template, String pathString, String requstMathod) {
        return template.replaceAll("\\$\\{METHOD}", requstMathod)
                       .replaceAll("\\$\\{PATH}", pathString);
    }

}
