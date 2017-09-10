package org.skywalking.apm.collector.core.module;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.skywalking.apm.collector.core.config.ConfigException;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.server.ServerException;
import org.skywalking.apm.collector.core.server.ServerHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public abstract class MultipleModuleInstaller extends ModuleConfigContainer {

    private final Logger logger = LoggerFactory.getLogger(MultipleModuleInstaller.class);

    public MultipleModuleInstaller() {
        moduleDefines = new LinkedList<>();
    }

    private List<ModuleDefine> moduleDefines;
    private ServerHolder serverHolder;

    @Override public final void injectServerHolder(ServerHolder serverHolder) {
        this.serverHolder = serverHolder;
    }

    @Override public final void preInstall() throws DefineException, ConfigException, ServerException {
        Map<String, Map> moduleConfig = getModuleConfig();
        Map<String, ModuleDefine> moduleDefineMap = getModuleDefineMap();

        Iterator<Map.Entry<String, ModuleDefine>> moduleDefineIterator = moduleDefineMap.entrySet().iterator();
        while (moduleDefineIterator.hasNext()) {
            Map.Entry<String, ModuleDefine> moduleDefineEntry = moduleDefineIterator.next();
            logger.info("module {} initialize", moduleDefineEntry.getKey());
            moduleDefineEntry.getValue().configParser().parse(moduleConfig.get(moduleDefineEntry.getKey()));
            moduleDefines.add(moduleDefineEntry.getValue());
            serverHolder.holdServer(moduleDefineEntry.getValue().server(), moduleDefineEntry.getValue().handlerList());
        }
    }

    @Override public void install() throws DefineException, ConfigException, ServerException {
        preInstall();

        CollectorContextHelper.INSTANCE.putContext(moduleContext());
        moduleDefines.forEach(moduleDefine -> {
            moduleDefine.initializeOtherContext();
        });
    }
}
