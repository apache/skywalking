package org.apache.skywalking.apm.plugin.shardingsphere.v500beta.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.NameMatch;

/**
 * {@link RouteInstrumentation} presents that skywalking intercepts {@link org.apache.shardingsphere.infra.route.engine.SQLRouteEngine}.
 */
public class RouteInstrumentation extends AbstractShardingSphereV500BetaInstrumentation {
    
    private static final String ENHANCE_CLASS = "org.apache.shardingsphere.infra.route.engine.SQLRouteEngine";
    
    private static final String EXECUTE_INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.shardingsphere.v500beta.RouteInterceptor";
    
    @Override
    public InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[]{
                new InstanceMethodsInterceptPoint() {
                    
                    @Override
                    public ElementMatcher<MethodDescription> getMethodsMatcher() {
                        return ElementMatchers.named("route");
                    }
                    
                    @Override
                    public String getMethodsInterceptor() {
                        return EXECUTE_INTERCEPTOR_CLASS;
                    }
                    
                    @Override
                    public boolean isOverrideArgs() {
                        return false;
                    }
                }
        };
    }
    
    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
    }
    
    @Override
    protected ClassMatch enhanceClass() {
        return NameMatch.byName(ENHANCE_CLASS);
    }
}
