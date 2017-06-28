package org.skywalking.apm.agent.core.plugin.interceptor.enhance;

import net.bytebuddy.implementation.bind.annotation.FieldValue;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;

/**
 * Get the value of dynamic added {@link ClassEnhancePluginDefine#CONTEXT_ATTR_NAME} field
 *
 * @author wusheng
 */
public class EnhancedInstanceFieldGetter {
    @RuntimeType
    public Object intercept(
        @FieldValue(ClassEnhancePluginDefine.CONTEXT_ATTR_NAME) Object fieldValue) {
        return fieldValue;
    }
}
