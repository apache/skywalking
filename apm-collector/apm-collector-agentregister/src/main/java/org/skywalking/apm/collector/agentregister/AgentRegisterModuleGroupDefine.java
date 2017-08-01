package org.skywalking.apm.collector.agentregister;

import org.skywalking.apm.collector.core.framework.Context;
import org.skywalking.apm.collector.core.module.ModuleGroupDefine;
import org.skywalking.apm.collector.core.module.ModuleInstaller;

/**
 * @author pengys5
 */
public class AgentRegisterModuleGroupDefine implements ModuleGroupDefine {

    public static final String GROUP_NAME = "agent_register";

    @Override public String name() {
        return GROUP_NAME;
    }

    @Override public Context groupContext() {
        return new AgentRegisterModuleContext(GROUP_NAME);
    }

    @Override public ModuleInstaller moduleInstaller() {
        return new AgentRegisterModuleInstaller();
    }
}
