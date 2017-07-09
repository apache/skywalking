package org.skywalking.apm.agent.test.tools;

import java.util.HashMap;
import java.util.LinkedList;
import org.junit.rules.ExternalResource;
import org.skywalking.apm.agent.core.boot.BootService;
import org.skywalking.apm.agent.core.boot.ServiceManager;
import org.skywalking.apm.agent.core.conf.RemoteDownstreamConfig;
import org.skywalking.apm.agent.core.context.IgnoredTracerContext;
import org.skywalking.apm.agent.core.context.TracingContext;
import org.skywalking.apm.agent.core.context.TracingContextListener;
import org.skywalking.apm.agent.test.helper.FieldSetter;

public class AgentServiceRule extends ExternalResource {

    @Override
    protected void after() {
        super.after();
        try {
            FieldSetter.setValue(ServiceManager.INSTANCE.getClass(), "bootedServices", new HashMap<Class, BootService>());
            FieldSetter.setValue(IgnoredTracerContext.ListenerManager.class, "LISTENERS", new LinkedList<TracingContextListener>());
            FieldSetter.setValue(TracingContext.ListenerManager.class, "LISTENERS", new LinkedList<TracingContextListener>());
        } catch (Exception e) {
        }
    }

    @Override
    protected void before() throws Throwable {
        super.before();
        ServiceManager.INSTANCE.boot();
        RemoteDownstreamConfig.Agent.APPLICATION_ID = 1;
        RemoteDownstreamConfig.Agent.APPLICATION_INSTANCE_ID = 1;
    }
}
