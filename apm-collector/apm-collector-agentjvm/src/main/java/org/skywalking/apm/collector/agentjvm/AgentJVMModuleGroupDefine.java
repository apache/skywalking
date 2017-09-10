package org.skywalking.apm.collector.agentjvm;

import org.skywalking.apm.collector.core.framework.Context;
import org.skywalking.apm.collector.core.module.ModuleGroupDefine;
import org.skywalking.apm.collector.core.module.ModuleInstaller;

/**
 * @author pengys5
 */
public class AgentJVMModuleGroupDefine implements ModuleGroupDefine {

    public static final String GROUP_NAME = "agent_jvm";

    private final AgentJVMCommonModuleInstaller installer;

    public AgentJVMModuleGroupDefine() {
        installer = new AgentJVMCommonModuleInstaller();
    }

    @Override public String name() {
        return GROUP_NAME;
    }

    @Override public Context groupContext() {
        return new AgentJVMModuleContext(GROUP_NAME);
    }

    @Override public ModuleInstaller moduleInstaller() {
        return installer;
    }
}
