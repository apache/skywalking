package org.skywalking.apm.agent.core.test.tools;

import java.util.HashMap;
import java.util.LinkedList;
import org.junit.rules.ExternalResource;
import org.powermock.reflect.Whitebox;
import org.skywalking.apm.agent.core.boot.BootService;
import org.skywalking.apm.agent.core.boot.ServiceManager;
import org.skywalking.apm.agent.core.context.IgnoredTracerContext;
import org.skywalking.apm.agent.core.context.TracingContext;
import org.skywalking.apm.agent.core.context.TracingContextListener;

public class AgentServiceRule extends ExternalResource {

    @Override
    protected void after() {
        super.after();
        Whitebox.setInternalState(ServiceManager.INSTANCE, "bootedServices", new HashMap<Class, BootService>());
        Whitebox.setInternalState(TracingContext.ListenerManager.class, "LISTENERS", new LinkedList<TracingContextListener>() );
        Whitebox.setInternalState(IgnoredTracerContext.ListenerManager.class, "LISTENERS", new LinkedList<TracingContextListener>() );
    }

    @Override
    protected void before() throws Throwable {
        super.before();
        ServiceManager.INSTANCE.boot();
    }
}
