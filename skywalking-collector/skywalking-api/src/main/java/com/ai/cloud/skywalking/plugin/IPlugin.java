package com.ai.cloud.skywalking.plugin;


import net.bytebuddy.dynamic.DynamicType;

public interface IPlugin {
    void define(DynamicType.Builder<?> builder) throws PluginException;
}
