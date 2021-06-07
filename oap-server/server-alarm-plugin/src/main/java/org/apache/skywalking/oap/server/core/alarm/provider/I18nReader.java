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

package org.apache.skywalking.oap.server.core.alarm.provider;

import lombok.Getter;
import lombok.Setter;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

/**
 * Rule Reader parses the given `zh-CN.yml` config file, to the target {@link Rules}.
 */
public class I18nReader {
    @Getter
    private Map yamlData;
    @Setter
    private String language;

    public I18nReader(InputStream inputStream) {
        Yaml yaml = new Yaml(new SafeConstructor());
        yamlData = (Map) yaml.load(inputStream);
    }

    public Rules readI18Nmessages() {
        Rules rules = new Rules();
        if (Objects.nonNull(yamlData)) {
            Map rulesData = (Map) ((Map) yamlData.get("language")).get("rules");
            if (rulesData == null) {
                return rules;
            }
            rules.setRules(new ArrayList<>());
            rulesData.forEach((k, v) -> {
                if (((String) k).endsWith("_rule")) {
                    AlarmRule alarmRule = new AlarmRule();
                    alarmRule.setAlarmRuleName((String) k);
                    Map settings = (Map) v;
                    alarmRule.setMessage(
                            (String) settings.getOrDefault("message", "Alarm caused by Rule " + alarmRule
                                    .getAlarmRuleName()));
                    rules.getRules().add(alarmRule);
                }
            });
        }
        return rules;
    }
}
