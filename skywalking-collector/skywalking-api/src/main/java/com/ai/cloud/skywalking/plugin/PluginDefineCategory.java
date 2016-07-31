package com.ai.cloud.skywalking.plugin;

import com.ai.cloud.skywalking.plugin.boot.IBootPluginDefine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PluginDefineCategory {

    private static PluginDefineCategory pluginDefineCategory;

    private final Map<String, AbstractClassEnhancePluginDefine> classEnhancePluginDefines = new HashMap<String, AbstractClassEnhancePluginDefine>();
    private final List<IBootPluginDefine>                       IBootPluginDefines        = new ArrayList<IBootPluginDefine>();

    private PluginDefineCategory(List<IPlugin> plugins) {
        for (IPlugin plugin : plugins) {
            if (plugin instanceof AbstractClassEnhancePluginDefine) {
                classEnhancePluginDefines.put(((AbstractClassEnhancePluginDefine) plugin).enhanceClassName(), (AbstractClassEnhancePluginDefine) plugin);
            }

            if (plugin instanceof IBootPluginDefine) {
                IBootPluginDefines.add((IBootPluginDefine) plugin);
            }
        }
    }

    public static PluginDefineCategory category(List<IPlugin> plugins) {
        if (pluginDefineCategory == null) {
            pluginDefineCategory = new PluginDefineCategory(plugins);
        }
        return pluginDefineCategory;
    }

    public List<IBootPluginDefine> getBootPluginsDefines() {
        return pluginDefineCategory.IBootPluginDefines;
    }

    public Map<String, AbstractClassEnhancePluginDefine> getClassEnhancePluginDefines() {
        return pluginDefineCategory.classEnhancePluginDefines;
    }

}
