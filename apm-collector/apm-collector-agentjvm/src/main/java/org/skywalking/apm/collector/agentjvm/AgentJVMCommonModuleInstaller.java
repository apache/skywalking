package org.skywalking.apm.collector.agentjvm;

import java.util.List;
import org.skywalking.apm.collector.core.framework.Context;
import org.skywalking.apm.collector.core.module.MultipleCommonModuleInstaller;

/**
 * @author pengys5
 */
public class AgentJVMCommonModuleInstaller extends MultipleCommonModuleInstaller {

    @Override public String groupName() {
        return AgentJVMModuleGroupDefine.GROUP_NAME;
    }

    @Override public Context moduleContext() {
        return new AgentJVMModuleContext(groupName());
    }

    @Override public List<String> dependenceModules() {
        return null;
    }
}
