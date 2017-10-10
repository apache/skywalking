/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.agent.core.plugin;

import org.skywalking.apm.agent.core.conf.Config;
import org.skywalking.apm.agent.core.plugin.exception.IllegalPluginDefineException;
import org.skywalking.apm.util.StringUtil;

public class PluginDefine {
    public static final String PLUGIN_OFF_PREFIX = "[OFF]";
    /**
     * Plugin name.
     */
    private String name;

    /**
     * The class name of plugin defined.
     */
    private String defineClass;

    /**
     * The sate of plugin.
     */
    private State state;

    private PluginDefine(String name, String defineClass, State state) {
        this.name = name;
        this.defineClass = defineClass;
        this.state = state;
    }

    public static PluginDefine build(String define) throws IllegalPluginDefineException {
        if (StringUtil.isEmpty(define)) {
            throw new IllegalPluginDefineException(define);
        }

        String[] pluginDefine = define.split("=");
        if (pluginDefine.length != 2) {
            throw new IllegalPluginDefineException(define);
        }

        String pluginName = pluginDefine[0];
        String defineClass = pluginDefine[1];
        if (pluginName.toUpperCase().startsWith(PLUGIN_OFF_PREFIX)) {
            return new PluginDefine(pluginName.substring(PLUGIN_OFF_PREFIX.length()), defineClass, State.OFF);
        } else {
            return new PluginDefine(pluginName, defineClass, State.ON);
        }
    }

    public boolean enable() {
        return !forceDisable() || forceEnable();
    }

    private boolean forceDisable() {
        return state != State.ON || Config.Plugin.DISABLED_PLUGINS.contains(name);
    }

    private boolean forceEnable() {
        return state == State.OFF && Config.Plugin.FORCE_ENABLE_PLUGINS.contains(name);
    }

    public String getDefineClass() {
        return defineClass;
    }

    private enum State {
        OFF, ON;
    }
}


