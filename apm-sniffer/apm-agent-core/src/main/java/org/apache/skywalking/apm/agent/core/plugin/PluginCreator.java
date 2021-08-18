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

import java.util.regex.Pattern;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.plugin.loader.AgentClassLoader;
import org.apache.skywalking.apm.util.StringUtil;

/**
 * create a AbstractClassEnhancePluginDefine instance according to {@link PluginDefine} and agent.config
 */
public class PluginCreator {

    private static Pattern extLoadPluginMatchPattern;

    static {
        String pluginMatchRule = Config.Plugin.PLUGINS_IN_EXT_CLASS_LOADER;
        if (StringUtil.isNotEmpty(pluginMatchRule)) {
            extLoadPluginMatchPattern = Pattern.compile(pluginMatchRule.replace(",", "|")
                                                                       .replace("*", ".*"));
        }
    }

    public static AbstractClassEnhancePluginDefine create(PluginDefine pluginDefine)
        throws ReflectiveOperationException {

        AbstractClassEnhancePluginDefine pluginDefineInstance =
            (AbstractClassEnhancePluginDefine) Class.forName(
                pluginDefine.getDefineClass(), true, AgentClassLoader.getDefault()).newInstance();

        if (extLoadPluginMatchPattern != null && extLoadPluginMatchPattern.matcher(pluginDefine.getName()).matches()) {
            pluginDefineInstance.setExtClassLoaderLoaded(true);
        }
        return pluginDefineInstance;
    }
}
