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

import java.io.FileNotFoundException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.configuration.api.GroupConfigChangeWatcher;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;

@Slf4j
public class EndpointNameGroupingRule4OpenapiWatcher extends GroupConfigChangeWatcher {
    private final EndpointNameGrouping grouping;
    private final Map<String, String> openapiDefs;

    public EndpointNameGroupingRule4OpenapiWatcher(ModuleProvider provider,
                                                   EndpointNameGrouping grouping) throws FileNotFoundException {
        super(CoreModule.NAME, provider, "endpoint-name-grouping-openapi");
        this.grouping = grouping;
        this.openapiDefs = new ConcurrentHashMap<>();
        this.grouping.setEndpointGroupingRule4Openapi(
            new EndpointGroupingRuleReader4Openapi("openapi-definitions").read());
    }

    @Override
    public Map<String, String> groupItems() {
        return openapiDefs;
    }

    @Override
    public void notifyGroup(final Map<String, ConfigChangeEvent> groupItems) {
        groupItems.forEach((groupItemName, event) -> {
            if (EventType.DELETE.equals(event.getEventType())) {
                this.openapiDefs.remove(groupItemName);
                log.info("EndpointNameGroupingRule4OpenapiWatcher removed groupItem: {}", groupItemName);
            } else {
                this.openapiDefs.put(groupItemName, event.getNewValue());
                log.info("EndpointNameGroupingRule4OpenapiWatcher modified groupItem: {}", groupItemName);
            }
        });
        this.grouping.setEndpointGroupingRule4Openapi(new EndpointGroupingRuleReader4Openapi(openapiDefs).read());
    }
}
