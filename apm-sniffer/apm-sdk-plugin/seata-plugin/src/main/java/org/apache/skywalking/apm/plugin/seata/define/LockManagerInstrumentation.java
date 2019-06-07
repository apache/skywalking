package org.apache.skywalking.apm.plugin.seata.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.apache.skywalking.apm.agent.core.plugin.match.HierarchyMatch.byHierarchyMatch;

/**
 * @author kezhenxu94
 */
public class LockManagerInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {
    private static final String ENHANCE_CLASS = "io.seata.server.lock.LockManager";
    private static final String INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.seata.interceptor.LockManagerInterceptor";

    public static final String ACQUIRE_LOCK = "acquireLock";
    public static final String RELEASE_LOCK = "releaseLock";
    public static final String IS_LOCKABLE = "isLockable";
    public static final String CLEAN_ALL_LOCKS = "cleanAllLocks";

    @Override
    protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
    }

    @Override
    protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(ACQUIRE_LOCK)
                        .or(named(RELEASE_LOCK))
                        .or(named(IS_LOCKABLE))
                        .or(named(CLEAN_ALL_LOCKS));
                }

                @Override
                public String getMethodsInterceptor() {
                    return INTERCEPTOR_CLASS;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            }
        };
    }

    @Override
    protected ClassMatch enhanceClass() {
        return byHierarchyMatch(new String[] {
            ENHANCE_CLASS
        });
    }
}
