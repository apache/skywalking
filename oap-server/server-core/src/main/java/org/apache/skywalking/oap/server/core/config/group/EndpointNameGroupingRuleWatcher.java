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

package org.apache.skywalking.oap.server.core.config.group;

import java.io.FileNotFoundException;
import java.io.StringReader;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;

/**
 * The config change watcher for endpoint name grouping rule.
 */
@Slf4j
public class EndpointNameGroupingRuleWatcher extends ConfigChangeWatcher {
    private final EndpointNameGrouping grouping;
    private volatile String ruleSetting;

    public EndpointNameGroupingRuleWatcher(ModuleProvider provider,
                                           EndpointNameGrouping grouping) throws FileNotFoundException {
        super(CoreModule.NAME, provider, "endpoint-name-grouping");
        this.grouping = grouping;
        // This is just a place holder text representing the original text.
        ruleSetting = "SkyWalking endpoint rule";
        grouping.setEndpointGroupingRule(new EndpointGroupingRuleReader(
            ResourceUtils.read("endpoint-name-grouping.yml")).read());
    }

    @Override
    public void notify(final ConfigChangeEvent value) {
        if (value.getEventType().equals(EventType.DELETE)) {
            ruleSetting = "";
            grouping.setEndpointGroupingRule(new EndpointGroupingRule());
        } else {
            ruleSetting = value.getNewValue();
            grouping.setEndpointGroupingRule(new EndpointGroupingRuleReader(new StringReader(ruleSetting)).read());
        }
    }

    @Override
    public String value() {
        return ruleSetting;
    }
}
