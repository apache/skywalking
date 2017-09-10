package org.skywalking.apm.collector.agentjvm;

import org.skywalking.apm.collector.core.framework.Context;
import org.skywalking.apm.collector.core.module.MultipleModuleInstaller;

/**
 * @author pengys5
 */
public class AgentJVMModuleInstaller extends MultipleModuleInstaller {

    @Override public String groupName() {
        return AgentJVMModuleGroupDefine.GROUP_NAME;
    }

    @Override public Context moduleContext() {
        return new AgentJVMModuleContext(groupName());
    }
}
