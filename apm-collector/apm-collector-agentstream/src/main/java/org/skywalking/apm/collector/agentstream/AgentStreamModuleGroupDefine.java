package org.skywalking.apm.collector.agentstream;

import org.skywalking.apm.collector.core.framework.Context;
import org.skywalking.apm.collector.core.module.ModuleGroupDefine;
import org.skywalking.apm.collector.core.module.ModuleInstaller;

/**
 * @author pengys5
 */
public class AgentStreamModuleGroupDefine implements ModuleGroupDefine {

    public static final String GROUP_NAME = "agent_stream";
    private final AgentStreamCommonModuleInstaller installer;

    public AgentStreamModuleGroupDefine() {
        installer = new AgentStreamCommonModuleInstaller();
    }

    @Override public String name() {
        return GROUP_NAME;
    }

    @Override public Context groupContext() {
        return new AgentStreamModuleContext(GROUP_NAME);
    }

    @Override public ModuleInstaller moduleInstaller() {
        return installer;
    }
}
