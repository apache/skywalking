package org.skywalking.apm.collector.core.module;

import java.util.List;
import java.util.Map;
import org.skywalking.apm.collector.core.CollectorException;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.config.ConfigException;
import org.skywalking.apm.collector.core.framework.Context;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.server.ServerException;
import org.skywalking.apm.collector.core.server.ServerHolder;

/**
 * @author pengys5
 */
public interface ModuleInstaller {

    List<String> dependenceModules();

    void injectServerHolder(ServerHolder serverHolder);

    String groupName();

    Context moduleContext();

    void injectConfiguration(Map<String, Map> moduleConfig, Map<String, ModuleDefine> moduleDefineMap);

    void preInstall() throws DefineException, ConfigException, ServerException;

    void install() throws ClientException, DefineException, ConfigException, ServerException;

    void afterInstall() throws CollectorException;
}
