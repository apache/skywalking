package org.apache.skywalking.apm.plugin.trace.ignore;

import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.context.AbstractTracerContext;
import org.apache.skywalking.apm.agent.core.context.ContextManagerExtendService;
import org.apache.skywalking.apm.agent.core.context.IgnoredTracerContext;
import org.apache.skywalking.apm.agent.core.context.TracingContext;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.plugin.trace.ignore.conf.IgnoreConfig;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.util.Assert;

/**
 * @author liujc [liujunc1993@163.com]
 * @date 2018/5/11 11:24
 */
public class TraceIgnoreTest {

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    @Test
    public void testServiceOverrideFromPlugin() {
        ContextManagerExtendService service = ServiceManager.INSTANCE.findService(ContextManagerExtendService.class);
        Assert.isInstanceOf(TraceIgnoreExtendService.class, service);
    }

    @Test
    public void testTraceIgnore() {
        ContextManagerExtendService service = ServiceManager.INSTANCE.findService(ContextManagerExtendService.class);
        IgnoreConfig.Trace.IGNORE_PATH = "/eureka/**";
        AbstractTracerContext ignoredTracerContext = service.createTraceContext("/eureka/apps", false);
        Assert.isInstanceOf(IgnoredTracerContext.class, ignoredTracerContext);

        AbstractTracerContext traceContext = service.createTraceContext("/consul/apps", false);
        Assert.isInstanceOf(TracingContext.class, traceContext);
    }

}
