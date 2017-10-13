package org.skywalking.apm.collector.core.module;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.skywalking.apm.collector.core.framework.DefineException;

public class ModuleConfigLoaderTestCase {

	@SuppressWarnings({ "rawtypes" })
	@Test
	public void testLoad() throws DefineException {
		ModuleConfigLoader configLoader = new ModuleConfigLoader();
        Map<String, Map> configuration = configLoader.load();
		Assert.assertNotNull(configuration.get("cluster"));
		Assert.assertNotNull(configuration.get("cluster").get("zookeeper"));
	}
}
