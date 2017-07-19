package org.skywalking.apm.collector.core.module;

import java.util.Map;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.server.ServerHolder;

/**
 * @author pengys5
 */
public interface ModuleInstaller {
    void install(Map<String, Map> moduleConfig,
        Map<String, ModuleDefine> moduleDefineMap, ServerHolder serverHolder) throws DefineException, ClientException;
}
