package org.skywalking.apm.collector.agentregister;

import java.util.List;
import org.skywalking.apm.collector.core.framework.Context;
import org.skywalking.apm.collector.core.module.MultipleCommonModuleInstaller;

/**
 * @author pengys5
 */
public class AgentRegisterCommonModuleInstaller extends MultipleCommonModuleInstaller {

    @Override public String groupName() {
        return AgentRegisterModuleGroupDefine.GROUP_NAME;
    }

    @Override public Context moduleContext() {
        return new AgentRegisterModuleContext(groupName());
    }

    @Override public List<String> dependenceModules() {
        return null;
    }
}
