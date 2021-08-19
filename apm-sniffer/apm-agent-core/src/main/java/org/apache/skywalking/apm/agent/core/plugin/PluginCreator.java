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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.loader.AgentClassLoader;
import org.apache.skywalking.apm.util.StringUtil;

/**
 * creator of AbstractClassEnhancePluginDefine instance according to {@link PluginDefine} and agent.config
 */
public class PluginCreator {

    private static final ILog LOGGER = LogManager.getLogger(PluginCreator.class);

    public static List<AbstractClassEnhancePluginDefine> create(List<PluginDefine> pluginDefines) {
        String pluginMatchRule = Config.Plugin.PLUGINS_IN_EXT_CLASS_LOADER;
        Pattern extLoadPluginMatchPattern = null;
        if (StringUtil.isNotEmpty(pluginMatchRule)) {
            extLoadPluginMatchPattern = Pattern.compile(pluginMatchRule.replace(",", "|")
                                                                       .replace("*", ".*"));
        }
        List<AbstractClassEnhancePluginDefine> plugins = new ArrayList<>();
        for (PluginDefine pluginDefine : pluginDefines) {
            LOGGER.debug("loading plugin class {}.", pluginDefine.getDefineClass());
            try {
                AbstractClassEnhancePluginDefine pluginDefineInstance =
                    (AbstractClassEnhancePluginDefine) Class.forName(
                        pluginDefine.getDefineClass(), true, AgentClassLoader.getDefault()).newInstance();
                if (extLoadPluginMatchPattern != null && extLoadPluginMatchPattern.matcher(pluginDefine.getName())
                                                                                  .matches()) {
                    pluginDefineInstance.setExtClassLoaderLoaded(true);
                }
                plugins.add(pluginDefineInstance);
            } catch (Throwable t) {
                LOGGER.error(t, "load plugin [{}] failure.", pluginDefine.getDefineClass());
            }
        }

        return plugins;
    }
}
