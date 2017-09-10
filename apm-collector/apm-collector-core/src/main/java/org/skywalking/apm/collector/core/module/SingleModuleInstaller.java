package org.skywalking.apm.collector.core.module;

import java.util.Iterator;
import java.util.Map;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.cluster.ClusterDataListenerDefine;
import org.skywalking.apm.collector.core.cluster.ClusterModuleException;
import org.skywalking.apm.collector.core.config.ConfigException;
import org.skywalking.apm.collector.core.framework.CollectorContextHelper;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.server.ServerException;
import org.skywalking.apm.collector.core.server.ServerHolder;
import org.skywalking.apm.collector.core.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public abstract class SingleModuleInstaller extends ModuleConfigContainer {

    private final Logger logger = LoggerFactory.getLogger(SingleModuleInstaller.class);

    private ModuleDefine moduleDefine;
    private ServerHolder serverHolder;

    @Override public final void injectServerHolder(ServerHolder serverHolder) {
        this.serverHolder = serverHolder;
    }

    @Override public final void preInstall() throws DefineException, ConfigException, ServerException {
        Map<String, Map> moduleConfig = getModuleConfig();
        Map<String, ModuleDefine> moduleDefineMap = getModuleDefineMap();
        if (CollectionUtils.isNotEmpty(moduleConfig)) {
            if (moduleConfig.size() > 1) {
                throw new ClusterModuleException("single module, but configure multiple modules");
            }

            Map.Entry<String, Map> configEntry = moduleConfig.entrySet().iterator().next();
            if (moduleDefineMap.containsKey(configEntry.getKey())) {
                moduleDefine = moduleDefineMap.get(configEntry.getKey());
                moduleDefine.configParser().parse(configEntry.getValue());
            } else {
                throw new ClusterModuleException("module name incorrect, please check the module name in application.yml");
            }
        } else {
            logger.info("could not configure module, use the default");
            Iterator<Map.Entry<String, ModuleDefine>> moduleDefineEntry = moduleDefineMap.entrySet().iterator();

            boolean hasDefaultModule = false;
            while (moduleDefineEntry.hasNext()) {
                if (moduleDefineEntry.next().getValue().defaultModule()) {
                    logger.info("module {} initialize", moduleDefine.getClass().getName());
                    if (hasDefaultModule) {
                        throw new ClusterModuleException("single module, but configure multiple default module");
                    }
                    moduleDefine = moduleDefineEntry.next().getValue();
                    moduleDefine.configParser().parse(null);
                    hasDefaultModule = true;
                }
            }
        }
        serverHolder.holdServer(moduleDefine.server(), moduleDefine.handlerList());
    }

    @Override public void install() throws ClientException, DefineException, ConfigException, ServerException {
        preInstall();
        moduleDefine.initializeOtherContext();

        CollectorContextHelper.INSTANCE.putContext(moduleContext());
        if (moduleDefine instanceof ClusterDataListenerDefine) {
            ClusterDataListenerDefine listenerDefine = (ClusterDataListenerDefine)moduleDefine;
            CollectorContextHelper.INSTANCE.getClusterModuleContext().getDataMonitor().addListener(listenerDefine.listener(), moduleDefine.registration());
        }
    }
}
