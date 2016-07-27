package com.ai.cloud.skywalking.plugin.boot;

import com.ai.cloud.skywalking.plugin.IPlugin;
import com.ai.cloud.skywalking.plugin.PluginException;
import net.bytebuddy.dynamic.DynamicType;

public abstract class BootPluginDefine implements IPlugin {

    @Override
    public void define(DynamicType.Builder<?> builder) throws PluginException {
        this.boot();
    }

    protected abstract void boot() throws BootException;

}
