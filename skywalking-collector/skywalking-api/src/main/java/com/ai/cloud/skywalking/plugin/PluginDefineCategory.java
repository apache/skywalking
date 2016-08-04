package com.ai.cloud.skywalking.plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PluginDefineCategory {

    private static PluginDefineCategory pluginDefineCategory;

    private final Map<String, AbstractClassEnhancePluginDefine> exactClassEnhancePluginDefineMapping  =
            new HashMap<String, AbstractClassEnhancePluginDefine>();
    private final Map<String, AbstractClassEnhancePluginDefine> blurryClassEnhancePluginDefineMapping =
            new HashMap<String, AbstractClassEnhancePluginDefine>();

    private PluginDefineCategory(List<AbstractClassEnhancePluginDefine> plugins) {
        for (AbstractClassEnhancePluginDefine plugin : plugins) {
            String enhanceClassName = plugin.enhanceClassName();
            if (enhanceClassName.endsWith("*")) {
                // 加上. 为了区分 com.ai.test  com.ai.test1
                blurryClassEnhancePluginDefineMapping
                        .put(enhanceClassName.substring(0, enhanceClassName.length() - 1), plugin);
            } else {
                exactClassEnhancePluginDefineMapping.put(enhanceClassName, plugin);
            }
        }
    }

    public static PluginDefineCategory category(List<AbstractClassEnhancePluginDefine> plugins) {
        if (pluginDefineCategory == null) {
            pluginDefineCategory = new PluginDefineCategory(plugins);
        }
        return pluginDefineCategory;
    }


    public Map<String, AbstractClassEnhancePluginDefine> getExactClassEnhancePluginDefineMapping() {
        return pluginDefineCategory.exactClassEnhancePluginDefineMapping;
    }

    public Map<String, AbstractClassEnhancePluginDefine> getBlurryClassEnhancePluginDefineMapping() {
        return blurryClassEnhancePluginDefineMapping;
    }

    public AbstractClassEnhancePluginDefine findPluginDefine(String enhanceClassName) {
        if (exactClassEnhancePluginDefineMapping.containsKey(enhanceClassName)) {
            return exactClassEnhancePluginDefineMapping.get(enhanceClassName);
        }

        for (Map.Entry<String, AbstractClassEnhancePluginDefine> entry : blurryClassEnhancePluginDefineMapping
                .entrySet()) {
            if (enhanceClassName.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }
}
