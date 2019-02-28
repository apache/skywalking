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

import java.io.*;
import java.util.*;
import org.yaml.snakeyaml.Yaml;

/**
 * Rule Reader parses the given `alarm-settings.yml` config file, to the target {@link Rules}.
 *
 * @author wusheng
 */
public class RulesReader {
    private Map yamlData;

    public RulesReader(InputStream inputStream) {
        Yaml yaml = new Yaml();
        yamlData = yaml.loadAs(inputStream, Map.class);
    }

    public RulesReader(Reader io) {
        Yaml yaml = new Yaml();
        yamlData = yaml.loadAs(io, Map.class);
    }

    public Rules readRules() {
        Rules rules = new Rules();

        if (Objects.nonNull(yamlData)) {
            Map rulesData = (Map)yamlData.get("rules");
            if (rulesData != null) {
                rules.setRules(new ArrayList<>());
                rulesData.forEach((k, v) -> {
                    if (((String)k).endsWith("_rule")) {
                        AlarmRule alarmRule = new AlarmRule();
                        alarmRule.setAlarmRuleName((String)k);
                        Map settings = (Map)v;
                        Object indicatorName = settings.get("indicator-name");
                        if (indicatorName == null) {
                            throw new IllegalArgumentException("indicator-name can't be null");
                        }

                        alarmRule.setIndicatorName((String)indicatorName);
                        alarmRule.setIncludeNames((ArrayList)settings.getOrDefault("include-names", new ArrayList(0)));
                        alarmRule.setThreshold(settings.get("threshold").toString());
                        alarmRule.setOp((String)settings.get("op"));
                        alarmRule.setPeriod((Integer)settings.getOrDefault("period", 1));
                        alarmRule.setCount((Integer)settings.getOrDefault("count", 1));
                        alarmRule.setSilencePeriod((Integer)settings.getOrDefault("silence-period", -1));
                        alarmRule.setMessage((String)settings.getOrDefault("message", "Alarm caused by Rule " + alarmRule.getAlarmRuleName()));

                        rules.getRules().add(alarmRule);
                    }
                });
            }
            List webhooks = (List)yamlData.get("webhooks");
            if (webhooks != null) {
                rules.setWebhooks(new ArrayList<>());
                webhooks.forEach(url -> {
                    rules.getWebhooks().add((String)url);
                });
            }
        }

        return rules;
    }
}
