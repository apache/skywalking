package org.skywalking.apm.collector.agentserver;

import org.skywalking.apm.collector.core.framework.Context;
import org.skywalking.apm.collector.core.module.ModuleGroupDefine;
import org.skywalking.apm.collector.core.module.ModuleInstaller;

/**
 * @author pengys5
 */
public class AgentServerModuleGroupDefine implements ModuleGroupDefine {

    public static final String GROUP_NAME = "agent_server";
    private final AgentServerCommonModuleInstaller installer;

    public AgentServerModuleGroupDefine() {
        installer = new AgentServerCommonModuleInstaller();
    }

    @Override public String name() {
        return GROUP_NAME;
    }

    @Override public Context groupContext() {
        return new AgentServerModuleContext(GROUP_NAME);
    }

    @Override public ModuleInstaller moduleInstaller() {
        return installer;
    }
}
