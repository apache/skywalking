package org.skywalking.apm.collector.core.config;

import org.junit.Test;
import org.skywalking.apm.collector.core.module.ModuleConfigLoader;
import org.skywalking.apm.collector.core.module.ModuleDefineException;

/**
 * @author pengys5
 */
public class ModuleConfigLoaderTestCase {

    @Test
    public void testLoad() throws ModuleDefineException {
        ModuleConfigLoader loader = new ModuleConfigLoader();
        loader.load();
    }
}
