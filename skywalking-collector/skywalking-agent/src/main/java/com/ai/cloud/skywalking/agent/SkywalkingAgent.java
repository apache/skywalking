package com.ai.cloud.skywalking.agent;

import com.ai.cloud.skywalking.agent.transformer.PluginsTransformer;
import com.ai.cloud.skywalking.conf.AuthDesc;
import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.conf.ConfigInitializer;
import com.ai.cloud.skywalking.plugin.IPlugin;
import com.ai.cloud.skywalking.plugin.PluginBootstrap;
import com.ai.cloud.skywalking.plugin.PluginCfg;
import com.ai.cloud.skywalking.plugin.interceptor.AbstractClassEnhancePluginDefine;
import com.ai.cloud.skywalking.plugin.interceptor.enhance.ClassEnhancePluginDefine;
import com.ai.cloud.skywalking.transformer.ClassTransformer;

import java.lang.instrument.Instrumentation;
import java.util.List;
import java.util.Map;

public class SkywalkingAgent {

    public static void premain(String agentArgs, Instrumentation inst) {
        ConfigInitializer.initialize();

        PluginBootstrap bootstrap = new PluginBootstrap();
        Map<String, ClassEnhancePluginDefine> pluginDefineMap = bootstrap.loadPlugins();

        if (AuthDesc.isAuth()) {
            inst.addTransformer(new PluginsTransformer(pluginDefineMap));
        }

        if (Config.SkyWalking.ALL_METHOD_MONITOR) {
            String interceptorPackage = System.getProperty("interceptor.package", "");
            inst.addTransformer(new ClassTransformer(interceptorPackage));
        }
    }
}
