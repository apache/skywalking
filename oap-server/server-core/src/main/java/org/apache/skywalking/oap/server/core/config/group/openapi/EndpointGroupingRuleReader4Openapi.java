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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public class EndpointGroupingRuleReader4Openapi {

    private final String openapiDefPath;
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

    public EndpointGroupingRuleReader4Openapi(final String openapiDefPath) {

        this.openapiDefPath = openapiDefPath;
    }

    public EndpointGroupingRule4Openapi read() throws FileNotFoundException {
        EndpointGroupingRule4Openapi endpointGroupingRule = new EndpointGroupingRule4Openapi();

        List<File> fileList = ResourceUtils.getDirectoryFilesRecursive(openapiDefPath, 1);
        for (File file : fileList) {
            if (!file.getName().endsWith(".yaml")) {
                continue;
            }
            Reader reader = new FileReader(file);
            Yaml yaml = new Yaml(new SafeConstructor());
            Map openapiData = yaml.load(reader);
            if (openapiData != null) {
                String serviceName = getServiceName(openapiData, file);
                LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap>> paths =
                    (LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap>>) openapiData.get("paths");

                if (paths != null) {
                    paths.forEach((pathString, pathItem) -> {
                        pathItem.keySet().forEach(key -> {
                            String requestMethod = requestMethodsMap.get(key);
                            if (!StringUtil.isEmpty(requestMethod)) {
                                String endpointGroupName = formatEndPointName(pathString, requestMethod, openapiData);
                                String groupRegex = getGroupRegex(pathString, requestMethod, openapiData);
                                if (isTemplatePath(pathString)) {
                                    endpointGroupingRule.addGroupedRule(serviceName, endpointGroupName, groupRegex);
                                } else {
                                    endpointGroupingRule.addDirectLookup(serviceName, groupRegex, endpointGroupName);
                                }
                            }
                        });
                    });
                }
            }
        }
        endpointGroupingRule.sortRulesAll();
        return endpointGroupingRule;
    }

    private String getServiceName(Map openapiData, File file) {

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
