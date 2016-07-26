package com.ai.cloud.skywalking.plugin.boot;

import com.ai.cloud.skywalking.plugin.IPlugin;
import com.ai.cloud.skywalking.plugin.exception.PluginException;

public abstract class BootPluginDefine implements IPlugin {

	@Override
	public byte[] define() throws PluginException {
		return this.boot();
	}
	
	protected abstract byte[] boot() throws BootException;

}
