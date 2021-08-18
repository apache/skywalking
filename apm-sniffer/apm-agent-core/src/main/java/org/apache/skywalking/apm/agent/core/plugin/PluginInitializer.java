package org.apache.skywalking.apm.agent.core.plugin;

import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.plugin.loader.AgentClassLoader;
import org.apache.skywalking.apm.util.StringUtil;

import java.util.regex.Pattern;

/**
 * @author dmz
 * @date Create in 1:06 2021/8/17
 */
public class PluginInitializer {
    /**
     * create and init
     */
    public static AbstractClassEnhancePluginDefine initialize(PluginDefine pluginDefine)
        throws ReflectiveOperationException {

        AbstractClassEnhancePluginDefine pluginDefineInstance = create(pluginDefine);

        init(pluginDefineInstance, pluginDefine.getName());

        return pluginDefineInstance;

    }
    /**
     * create instance by reflection
     */
    private static AbstractClassEnhancePluginDefine create(PluginDefine pluginDefine)
        throws ReflectiveOperationException {
        Class<?> pluginDefineClass = Class.forName(pluginDefine.getDefineClass(), true, AgentClassLoader.getDefault());
        return (AbstractClassEnhancePluginDefine)pluginDefineClass.newInstance();
    }

    /**
     * just set isExtInstrumentation property in {@link AbstractClassEnhancePluginDefine}
     */
    private static void init(AbstractClassEnhancePluginDefine instance, String pluginName) {
        if (StringUtil.isNotEmpty(Config.Plugin.PLUGINS_IN_EXT_CLASS_LOADER)) {
            String[] pluginMatchRules = Config.Plugin.PLUGINS_IN_EXT_CLASS_LOADER.split(",");
            for (String pluginMatchRule : pluginMatchRules) {
                final Pattern p = Pattern.compile(pluginMatchRule.replace("*", ".*"));
                if (p.matcher(pluginName).matches()) {
                    instance.setExtClassLoaderLoaded(true);
                }
            }
        }
    }
}
