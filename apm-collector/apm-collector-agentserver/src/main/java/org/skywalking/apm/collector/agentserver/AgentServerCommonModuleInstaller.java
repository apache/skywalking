package org.skywalking.apm.collector.agentserver;

import java.util.List;
import org.skywalking.apm.collector.core.framework.Context;
import org.skywalking.apm.collector.core.module.MultipleCommonModuleInstaller;

/**
 * @author pengys5
 */
public class AgentServerCommonModuleInstaller extends MultipleCommonModuleInstaller {

    @Override public String groupName() {
        return AgentServerModuleGroupDefine.GROUP_NAME;
    }

    @Override public Context moduleContext() {
        return new AgentServerModuleContext(groupName());
    }

    @Override public List<String> dependenceModules() {
        return null;
    }
}
