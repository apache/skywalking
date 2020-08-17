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

package org.apache.skywalking.apm.agent.core.plugin;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.skywalking.apm.agent.core.conf.Config;

import static org.apache.skywalking.apm.agent.core.conf.Config.Plugin.EXCLUDE_PLUGINS;

/**
 * Select some plugins in activated plugins
 */
public class PluginSelector {
    /**
     * Exclude activated plugins
     *
     * @param pluginDefines the pluginDefines is loaded from activations directory or plugins directory
     * @return real activate plugins
     * @see Config.Plugin#EXCLUDE_PLUGINS
     */
    public List<PluginDefine> select(List<PluginDefine> pluginDefines) {
        if (!EXCLUDE_PLUGINS.isEmpty()) {
            List<String> excludes = Arrays.asList(EXCLUDE_PLUGINS.toLowerCase().split(","));
            return pluginDefines.stream()
                                .filter(item -> !excludes.contains(item.getName().toLowerCase()))
                                .collect(Collectors.toList());
        }
        return pluginDefines;
    }
}
