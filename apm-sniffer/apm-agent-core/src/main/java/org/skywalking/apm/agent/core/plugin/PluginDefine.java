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

    private PluginDefine(String name, String defineClass) {
        this.name = name;
        this.defineClass = defineClass;
    }

    public static PluginDefine build(String define) throws IllegalPluginDefineException {
        if (StringUtil.isEmpty(define)) {
            throw new IllegalPluginDefineException(define);
        }

        String[] pluginDefine = define.split("=");
        if (pluginDefine.length != 2) {
            throw new IllegalPluginDefineException(define);
        }

        return new PluginDefine(pluginDefine[0], pluginDefine[1]);
    }

    public boolean enable() {
        return !Config.Plugin.DISABLED_PLUGINS.contains(name);
    }

    public String getDefineClass() {
        return defineClass;
    }
}


