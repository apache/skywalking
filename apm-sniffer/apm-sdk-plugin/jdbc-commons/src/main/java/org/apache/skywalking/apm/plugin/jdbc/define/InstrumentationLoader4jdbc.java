package org.apache.skywalking.apm.plugin.jdbc.define;

import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.apm.agent.core.plugin.AbstractClassEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.loader.AgentClassLoader;
import org.apache.skywalking.apm.agent.core.plugin.loader.InstrumentationLoader;
import org.apache.skywalking.apm.dependencies.com.google.common.collect.Lists;

public class InstrumentationLoader4jdbc implements InstrumentationLoader {

    @Override
    public List<AbstractClassEnhancePluginDefine> load(AgentClassLoader classLoader) {
        final ArrayList<AbstractClassEnhancePluginDefine> list = Lists.newArrayList();
        final String cls = System.getProperty("skywalking_datasource_intercept_class");
        if (cls != null) {
            list.add(new DataSourceInstrumentation(cls));
        }
        return  list;
    }


}
