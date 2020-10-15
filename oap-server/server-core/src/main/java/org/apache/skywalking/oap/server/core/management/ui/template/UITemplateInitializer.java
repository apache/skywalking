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

package org.apache.skywalking.oap.server.core.management.ui.template;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.query.enumeration.TemplateType;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.yaml.snakeyaml.Yaml;

/**
 * UITemplateInitializer load the template from the config file in YAML format. The template definition is by JSON
 * format in default, but it depends on the UI implementation only.
 */
@Slf4j
public class UITemplateInitializer {
    private Map yamlData;

    public UITemplateInitializer(InputStream inputStream) {
        Yaml yaml = new Yaml();
        try {
            yamlData = yaml.loadAs(inputStream, Map.class);
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                log.warn(e.getMessage(), e);
            }
        }
    }

    public List<UITemplate> read() {
        List<UITemplate> uiTemplates = new ArrayList<>();
        if (Objects.nonNull(yamlData)) {
            List templates = (List) yamlData.get("templates");
            if (templates != null) {
                templates.forEach(templateObj -> {
                    final Map template = (Map) templateObj;
                    UITemplate newTemplate = new UITemplate();
                    final String name = (String) template.get("name");
                    if (StringUtil.isEmpty(name)) {
                        throw new IllegalArgumentException("template name shouldn't be null");
                    }
                    newTemplate.setName(name);
                    final String type = (String) template.getOrDefault("type", TemplateType.DASHBOARD.name());
                    TemplateType.forName(type); // for checking.
                    newTemplate.setType(type);
                    final String configuration = (String) template.get("configuration");
                    if (StringUtil.isEmpty(configuration)) {
                        throw new IllegalArgumentException("template configuration shouldn't be null");
                    }
                    newTemplate.setConfiguration(configuration);
                    newTemplate.setActivated(
                        BooleanUtils.booleanToValue(
                            // The template should be activated in default, it is just an option.
                            (Boolean) template.getOrDefault("activated", false)
                        )
                    );
                    newTemplate.setDisabled(
                        BooleanUtils.booleanToValue(
                            // The template should be available in default.
                            (Boolean) template.getOrDefault("disabled", false)
                        )
                    );
                    if (uiTemplates.contains(newTemplate)) {
                        throw new IllegalArgumentException("Template " + newTemplate.getName() + " name conflicts");
                    }
                    uiTemplates.add(newTemplate);
                });
            }
        }
        return uiTemplates;
    }
}
