package com.a.eye.skywalking.plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Just category the plugins.
 * Change the store structure from {@link List} to {@link Map}
 */
public class PluginDefineCategory {

    private static PluginDefineCategory pluginDefineCategory;

    private final Map<String, AbstractClassEnhancePluginDefine> exactClassEnhancePluginDefineMapping = new HashMap<String, AbstractClassEnhancePluginDefine>();

    private PluginDefineCategory(List<AbstractClassEnhancePluginDefine> plugins) {
        for (AbstractClassEnhancePluginDefine plugin : plugins) {
            String enhanceClassName = plugin.enhanceClassName();

            if (enhanceClassName == null) {
                continue;
            }

            exactClassEnhancePluginDefineMapping.put(enhanceClassName, plugin);
        }
    }

    public static PluginDefineCategory category(List<AbstractClassEnhancePluginDefine> plugins) {
        if (pluginDefineCategory == null) {
            pluginDefineCategory = new PluginDefineCategory(plugins);
        }
        return pluginDefineCategory;
    }

    public AbstractClassEnhancePluginDefine findPluginDefine(String enhanceClassName) {
        if (exactClassEnhancePluginDefineMapping.containsKey(enhanceClassName)) {
            return exactClassEnhancePluginDefineMapping.get(enhanceClassName);
        }

        return null;
    }
}
