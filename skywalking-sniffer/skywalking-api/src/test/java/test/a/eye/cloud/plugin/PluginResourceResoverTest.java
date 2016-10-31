package test.a.eye.cloud.plugin;

import java.io.IOException;

import com.a.eye.skywalking.plugin.PluginResourcesResolver;

public class PluginResourceResoverTest {

	public static void main(String[] args) throws IOException {
		PluginResourcesResolver resolver = new PluginResourcesResolver();
		resolver.getResources();
	}

}
