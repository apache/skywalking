package org.skywalking.apm.agent.core.plugin;

import net.bytebuddy.dynamic.DynamicType;

/**
 * Created by wusheng on 2017/2/27.
 */
public class MockAbstractClassEnhancePluginDefine extends AbstractClassEnhancePluginDefine {

    @Override
    protected DynamicType.Builder<?> enhance(String enhanceOriginClassName, DynamicType.Builder<?> newClassBuilder,
        ClassLoader classLoader) throws PluginException {
        return newClassBuilder;
    }

    @Override
    protected String enhanceClassName() {
        return "NotExistClass";
    }
}
