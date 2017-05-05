package org.skywalking.apm.agent.core.plugin;

import java.util.List;
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

    private PluginDefine() {
    }

    private PluginDefine(String name, String defineClass) {
        this.name = name;
        this.defineClass = defineClass;
    }

    public static PluginDefine build(String define) {
        if (StringUtil.isEmpty(define)) {
            return IllegalPluginDefine.INSTANCE;
        }

        String[] pluginDefine = define.split("=");
        if (pluginDefine.length != 2) {
            return IllegalPluginDefine.INSTANCE;
        }

        return new PluginDefine(pluginDefine[0], pluginDefine[1]);
    }

    public boolean disabled(List<String> disablePlugins) {
        return disablePlugins.contains(name);
    }

    public String getDefineClass() {
        return defineClass;
    }

    static class IllegalPluginDefine extends PluginDefine {

        static final PluginDefine INSTANCE = new IllegalPluginDefine();

        @Override
        public boolean disabled(List<String> disablePlugins) {
            return true;
        }
    }
}


