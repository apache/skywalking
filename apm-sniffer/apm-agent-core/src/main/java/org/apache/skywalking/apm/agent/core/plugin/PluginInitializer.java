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

import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.plugin.loader.AgentClassLoader;
import org.apache.skywalking.apm.util.StringUtil;

import java.util.regex.Pattern;

/**
 * create a AbstractClassEnhancePluginDefine instance according to {@link PluginDefine} and init it with agent.config
 */
public class PluginInitializer {

    public static AbstractClassEnhancePluginDefine initialize(PluginDefine pluginDefine)
        throws ReflectiveOperationException {

        AbstractClassEnhancePluginDefine pluginDefineInstance = create(pluginDefine);

        init(pluginDefineInstance, pluginDefine.getName());

        return pluginDefineInstance;

    }

    private static AbstractClassEnhancePluginDefine create(PluginDefine pluginDefine)
        throws ReflectiveOperationException {
        Class<?> pluginDefineClass = Class.forName(pluginDefine.getDefineClass(), true, AgentClassLoader.getDefault());
        return (AbstractClassEnhancePluginDefine) pluginDefineClass.newInstance();
    }

    /**
     * Set isExtInstrumentation property in {@link AbstractClassEnhancePluginDefine} according to agent.config
     */
    private static void init(AbstractClassEnhancePluginDefine instance, String pluginName) {
        if (StringUtil.isNotEmpty(Config.Plugin.PLUGINS_IN_EXT_CLASS_LOADER)) {
            String[] pluginMatchRules = Config.Plugin.PLUGINS_IN_EXT_CLASS_LOADER.split(",");
            for (String pluginMatchRule : pluginMatchRules) {
                final Pattern p = Pattern.compile(pluginMatchRule.replace("*", ".*"));
                if (p.matcher(pluginName).matches()) {
                    instance.setExtClassLoaderLoaded(true);
                }
            }
        }
    }
}
