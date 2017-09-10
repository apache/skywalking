package org.skywalking.apm.collector.agentregister;

import org.skywalking.apm.collector.core.framework.Context;
import org.skywalking.apm.collector.core.module.MultipleModuleInstaller;

/**
 * @author pengys5
 */
public class AgentRegisterModuleInstaller extends MultipleModuleInstaller {

    @Override public String groupName() {
        return AgentRegisterModuleGroupDefine.GROUP_NAME;
    }

    @Override public Context moduleContext() {
        return new AgentRegisterModuleContext(groupName());
    }
}
