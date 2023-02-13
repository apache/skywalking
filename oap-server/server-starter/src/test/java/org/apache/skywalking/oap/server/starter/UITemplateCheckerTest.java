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

package org.apache.skywalking.oap.server.starter;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplateInitializer;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * @since 9.0.0 SkyWalking migrate to skywalking-booster-ui, the configs are changed. This test verifies whether the
 * config files are legal, otherwise would fail, in order to block the merge.
 */
public class UITemplateCheckerTest {
    @Test
    public void validateUITemplate() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(JsonParser.Feature.ALLOW_COMMENTS);
        Set<String> dashboardIds = new HashSet<>();
        Set<String> dashboardNames = new HashSet<>();
        for (String folder : UITemplateInitializer.UI_TEMPLATE_FOLDER) {
            File[] templateFiles = ResourceUtils.getPathFiles("ui-initialized-templates/" + folder.toLowerCase(
                Locale.ROOT));
            for (File template : templateFiles) {
                JsonNode jsonNode = mapper.readTree(template);
                if (jsonNode == null || jsonNode.size() == 0) {
                    continue;
                }
                if (jsonNode.size() > 1) {
                    throw new IllegalArgumentException(
                        "File:  " + template.getName() + " should be only one dashboard setting json object.");
                }

                JsonNode configNode = jsonNode.get(0).get("configuration");
                String inId = jsonNode.get(0).get("id").textValue();
                String inName = configNode.get("name").textValue();
                String inLayer = configNode.get("layer").textValue();
                String inEntity = configNode.get("entity").textValue();
                Assertions.assertFalse(dashboardIds.contains(inId), "File: " + template + " has duplicate id: " + inId);
                dashboardIds.add(inId);
                String nameKey = StringUtil.join('_', inLayer, inEntity, inName);
                Assertions.assertFalse(dashboardNames.contains(nameKey), "File:" + template + " has duplicate name: " + inName);
                dashboardNames.add(nameKey);

                //Todo: implement more validation.
            }
        }
    }
}
