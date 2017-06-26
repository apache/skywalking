package org.skywalking.apm.agent.core.plugin;

import org.skywalking.apm.agent.core.conf.Config;
import org.skywalking.apm.agent.core.plugin.exception.IllegalPluginDefineException;
import org.skywalking.apm.util.StringUtil;

public class PluginDefine {
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
        if (pluginName.toUpperCase().startsWith("[OFF]")) {
            return new PluginDefine(pluginName.substring(5), defineClass, State.OFF);
        } else {
            return new PluginDefine(pluginName, defineClass, State.ON);
        }
    }

    public boolean enable() {
        return !Config.Plugin.DISABLED_PLUGINS.contains(name) || forceEnable();
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


