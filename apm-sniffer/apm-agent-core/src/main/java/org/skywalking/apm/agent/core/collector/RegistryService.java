package org.skywalking.apm.agent.core.collector;

import org.skywalking.apm.agent.core.boot.BootService;
import org.skywalking.apm.agent.core.collector.sender.InstanceRegistrySender;
import org.skywalking.apm.agent.core.collector.task.InstanceRegistryTask;
import org.skywalking.apm.agent.core.conf.Config;

/**
 * {@link RegistryService} is responsible for registry agent instance. and it start a timer
 * task to retry it if agent register failed.
 *
 * @author zhang xin
 */
public class RegistryService implements BootService {
    @Override
    public void bootUp() throws Throwable {
        InstanceRegistrySender sender = new InstanceRegistrySender();
        final InstanceRegistryTask task = new InstanceRegistryTask(sender);
        sender.addListener(new InstanceRegistrySender.Listener() {
            @Override
            public void success(int instanceId) {
                Config.Agent.INSTANCE_ID = instanceId;
                task.stop();
            }
        });

        task.start();
    }
}
