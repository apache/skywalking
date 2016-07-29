package com.ai.cloud.skywalking.agent;

import com.ai.cloud.skywalking.conf.AuthDesc;
import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.conf.ConfigInitializer;
import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;
import com.ai.cloud.skywalking.plugin.*;
import com.ai.cloud.skywalking.plugin.boot.BootPluginDefine;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.instrument.Instrumentation;
import java.util.List;

public class SkywalkingAgent {

    private static Logger logger = LogManager.getLogger(SkywalkingAgent.class);

    public static void premain(String agentArgs, Instrumentation instrumentation) throws PluginException {
        Config.SkyWalking.IS_PREMAIN_MODE = true;
        if (!AuthDesc.isAuth()) {
            List<IPlugin> plugins = new PluginBootstrap().loadPlugins();
            final PluginDefineCategory pluginDefineCategory = PluginDefineCategory.category(plugins);

            startBootPluginDefines(pluginDefineCategory.getBootPluginsDefines());

            new AgentBuilder.Default().type(ElementMatchers.<TypeDescription>any()).transform(new AgentBuilder.Transformer() {
                public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader) {
                    AbstractClassEnhancePluginDefine pluginDefine = pluginDefineCategory.getClassEnhancePluginDefines().get(typeDescription.getTypeName());
                    if (pluginDefine == null) {
                        return builder;
                    }

                    try {
                        return pluginDefine.define0(builder);
                    } catch (PluginException e) {
                        logger.error("Failed to enhance plugin " + pluginDefine.getClass().getName(), e);
                        return builder;
                    }
                }
            }).installOn(instrumentation);

        }
    }


    public static void startBootPluginDefines(List<BootPluginDefine> bootPluginDefines) throws PluginException {
        for (BootPluginDefine bootPluginDefine : bootPluginDefines) {
            bootPluginDefine.define(null);
        }
    }
}
