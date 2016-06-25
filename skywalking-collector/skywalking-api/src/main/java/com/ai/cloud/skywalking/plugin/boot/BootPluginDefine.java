package com.ai.cloud.skywalking.plugin.boot;

import com.ai.cloud.skywalking.plugin.IPlugin;
import com.ai.cloud.skywalking.plugin.PluginException;

public abstract class BootPluginDefine implements IPlugin {

	@Override
	public void define() throws PluginException {
		this.boot();
	}
	
	protected abstract void boot() throws BootException;

}
