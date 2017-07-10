package org.skywalking.apm.collector.core.config;

import java.io.FileNotFoundException;
import org.junit.Test;
import org.skywalking.apm.collector.core.module.ModuleConfigLoader;
import org.skywalking.apm.collector.core.module.ModuleConfigLoaderException;

/**
 * @author pengys5
 */
public class ModuleConfigLoaderTestCase {

    @Test
    public void testLoad() throws ModuleConfigLoaderException {
        ModuleConfigLoader loader = new ModuleConfigLoader();
        loader.load();
    }
}
