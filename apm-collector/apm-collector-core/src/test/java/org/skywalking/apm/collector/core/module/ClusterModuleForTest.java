package org.skywalking.apm.collector.core.module;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ClusterModuleForTest implements Module {

    private final Logger logger = LoggerFactory.getLogger(ModuleInstaller.class);

    @Override public void install(Map configuration) {
        logger.debug(configuration.toString());
    }
}
