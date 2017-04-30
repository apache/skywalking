package org.skywalking.apm.agent.core.plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The <code>PluginFinder</code> represents a finder , which assist to find the one
 * from the given {@link AbstractClassEnhancePluginDefine} list, by name match.
 *
 * @author wusheng
 */
public class PluginFinder {
    private final Map<String, AbstractClassEnhancePluginDefine> pluginDefineMap = new HashMap<String, AbstractClassEnhancePluginDefine>();

    public PluginFinder(List<AbstractClassEnhancePluginDefine> plugins) {
        for (AbstractClassEnhancePluginDefine plugin : plugins) {
            String enhanceClassName = plugin.enhanceClassName();

            if (enhanceClassName == null) {
                continue;
            }

            pluginDefineMap.put(enhanceClassName, plugin);
        }
    }

    public AbstractClassEnhancePluginDefine find(String enhanceClassName) {
        if (pluginDefineMap.containsKey(enhanceClassName)) {
            return pluginDefineMap.get(enhanceClassName);
        }

        throw new PluginException("Can not find plugin:" + enhanceClassName);
    }

    public boolean exist(String enhanceClassName) {
        return pluginDefineMap.containsKey(enhanceClassName);
    }
}
