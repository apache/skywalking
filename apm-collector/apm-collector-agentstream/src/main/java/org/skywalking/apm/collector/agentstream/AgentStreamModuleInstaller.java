package org.skywalking.apm.collector.agentstream;

import org.skywalking.apm.collector.agentstream.worker.storage.PersistenceTimer;
import org.skywalking.apm.collector.core.config.ConfigException;
import org.skywalking.apm.collector.core.framework.Context;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.module.MultipleModuleInstaller;
import org.skywalking.apm.collector.core.server.ServerException;

/**
 * @author pengys5
 */
public class AgentStreamModuleInstaller extends MultipleModuleInstaller {

    @Override public String groupName() {
        return AgentStreamModuleGroupDefine.GROUP_NAME;
    }

    @Override public Context moduleContext() {
        return new AgentStreamModuleContext(groupName());
    }

    @Override public void install() throws DefineException, ConfigException, ServerException {
        super.install();
        new PersistenceTimer().start();
    }
}
