package org.skywalking.apm.collector.queue;

import java.util.Map;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.module.ModuleDefine;
import org.skywalking.apm.collector.core.module.ModuleInstaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class QueueModuleInstaller implements ModuleInstaller {

    private final Logger logger = LoggerFactory.getLogger(QueueModuleInstaller.class);

    @Override public void install(Map<String, Map> moduleConfig,
        Map<String, ModuleDefine> moduleDefineMap) throws DefineException, ClientException {
        logger.info("beginning cluster module install");

    }
}
