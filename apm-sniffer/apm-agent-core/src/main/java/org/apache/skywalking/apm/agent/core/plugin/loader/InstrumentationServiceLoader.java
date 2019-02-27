package org.apache.skywalking.apm.agent.core.plugin.loader;

import org.apache.skywalking.apm.agent.core.plugin.AbstractClassEnhancePluginDefine;

import java.util.List;

/**
 * @Author: zhaoyuguang
 * @Date: 2019/2/27 9:14 AM
 */

public interface InstrumentationServiceLoader {

    List<AbstractClassEnhancePluginDefine> load(AgentClassLoader classLoader);
}
