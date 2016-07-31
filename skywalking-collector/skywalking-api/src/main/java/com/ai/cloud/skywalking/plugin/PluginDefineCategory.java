package com.ai.cloud.skywalking.plugin;

import com.ai.cloud.skywalking.plugin.boot.IBootPluginDefine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PluginDefineCategory {

    private static PluginDefineCategory pluginDefineCategory;

    private final Map<String, AbstractClassEnhancePluginDefine> exactClassEnhancePluginDefineMapping  =
            new HashMap<String, AbstractClassEnhancePluginDefine>();
    private final List<IBootPluginDefine>                       IBootPluginDefines                    =
            new ArrayList<IBootPluginDefine>();
    private final Map<String, AbstractClassEnhancePluginDefine> blurryClassEnhancePluginDefineMapping =
            new HashMap<String, AbstractClassEnhancePluginDefine>();

    private PluginDefineCategory(List<IPlugin> plugins) {
        for (IPlugin plugin : plugins) {
            if (plugin instanceof AbstractClassEnhancePluginDefine) {
                String enhanceClassName = ((AbstractClassEnhancePluginDefine) plugin).enhanceClassName();
                if (enhanceClassName.endsWith("*")) {
                    // 加上. 为了区分 com.ai.test  com.ai.test1
                    blurryClassEnhancePluginDefineMapping
                            .put(enhanceClassName.substring(0, enhanceClassName.length() - 1),
                                    (AbstractClassEnhancePluginDefine) plugin);
                } else {
                    exactClassEnhancePluginDefineMapping
                            .put(enhanceClassName, (AbstractClassEnhancePluginDefine) plugin);
                }
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

    public Map<String, AbstractClassEnhancePluginDefine> getExactClassEnhancePluginDefineMapping() {
        return pluginDefineCategory.exactClassEnhancePluginDefineMapping;
    }

    public Map<String, AbstractClassEnhancePluginDefine> getBlurryClassEnhancePluginDefineMapping() {
        return blurryClassEnhancePluginDefineMapping;
    }

    public AbstractClassEnhancePluginDefine findPluginDefine(String enhanceClassName) {
        if (exactClassEnhancePluginDefineMapping.containsKey(enhanceClassName)){
            return exactClassEnhancePluginDefineMapping.get(enhanceClassName);
        }

        for (Map.Entry<String, AbstractClassEnhancePluginDefine> entry : blurryClassEnhancePluginDefineMapping.entrySet()){
            if (enhanceClassName.startsWith(entry.getKey())){
                return entry.getValue();
            }
        }

        return null;
    }
}
