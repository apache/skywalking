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
import lombok.extern.slf4j.Slf4j;
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
        //Todo: implement later when new template file ready
        return uiTemplates;
    }
}
