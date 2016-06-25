package com.ai.cloud.skywalking.agent;

import com.ai.cloud.skywalking.plugin.PluginBootstrap;

import java.lang.instrument.Instrumentation;

public class SkywalkingAgent {

    public static void premain(String agentArgs, Instrumentation inst) {
        PluginBootstrap bootstrap = new PluginBootstrap();
        bootstrap.start();
    }
}
