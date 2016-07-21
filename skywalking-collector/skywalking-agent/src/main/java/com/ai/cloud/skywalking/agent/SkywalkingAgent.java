package com.ai.cloud.skywalking.agent;

import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.conf.ConfigInitializer;
import com.ai.cloud.skywalking.plugin.PluginBootstrap;
import com.ai.cloud.skywalking.transformer.ClassTransformer;

import java.lang.instrument.Instrumentation;

public class SkywalkingAgent {

    public static void premain(String agentArgs, Instrumentation inst) {
        ConfigInitializer.initialize();
        if (Config.SkyWalking.ALL_METHOD_MONITOR){
            inst.addTransformer(new ClassTransformer());
        }

        PluginBootstrap bootstrap = new PluginBootstrap();
        bootstrap.start();
    }
}
