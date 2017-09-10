package org.skywalking.apm.collector.agentserver;

import org.skywalking.apm.collector.core.framework.Context;
import org.skywalking.apm.collector.core.module.MultipleModuleInstaller;

/**
 * @author pengys5
 */
public class AgentServerModuleInstaller extends MultipleModuleInstaller {

    @Override public String groupName() {
        return AgentServerModuleGroupDefine.GROUP_NAME;
    }

    @Override public Context moduleContext() {
        return new AgentServerModuleContext(groupName());
    }
}
