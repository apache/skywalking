package org.skywalking.apm.collector.remote;

import java.util.Map;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.module.ModuleDefine;
import org.skywalking.apm.collector.core.module.ModuleInstaller;

/**
 * @author pengys5
 */
public class RemoteModuleInstaller implements ModuleInstaller {
    @Override public void install(Map<String, Map> moduleConfig,
        Map<String, ModuleDefine> moduleDefineMap) throws DefineException, ClientException {

    }
}
