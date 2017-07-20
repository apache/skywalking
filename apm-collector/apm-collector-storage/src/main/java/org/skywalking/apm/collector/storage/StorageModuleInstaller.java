package org.skywalking.apm.collector.storage;

import java.util.Map;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.module.ModuleDefine;
import org.skywalking.apm.collector.core.module.SingleModuleInstaller;
import org.skywalking.apm.collector.core.server.ServerHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class StorageModuleInstaller extends SingleModuleInstaller {

    private final Logger logger = LoggerFactory.getLogger(StorageModuleInstaller.class);

    @Override public void install(Map<String, Map> moduleConfig,
        Map<String, ModuleDefine> moduleDefineMap, ServerHolder serverHolder) throws DefineException, ClientException {
        logger.info("beginning agent stream module install");

        StorageModuleContext context = new StorageModuleContext(StorageModuleGroupDefine.GROUP_NAME);
        CollectorContextHelper.INSTANCE.putContext(context);

        installSingle(moduleConfig, moduleDefineMap, serverHolder);
    }
}
