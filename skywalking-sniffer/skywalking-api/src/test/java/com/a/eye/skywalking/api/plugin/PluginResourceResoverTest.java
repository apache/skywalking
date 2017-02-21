package com.a.eye.skywalking.api.plugin;

import java.io.IOException;

public class PluginResourceResoverTest {

	public static void main(String[] args) throws IOException {
		PluginResourcesResolver resolver = new PluginResourcesResolver();
		resolver.getResources();
	}

}
