package org.skywalking.apm.agent.core.collector.task;

import org.skywalking.apm.agent.core.collector.Sender;
import org.skywalking.apm.agent.core.collector.entity.Instance;
import org.skywalking.apm.agent.core.conf.Config;

/**
 * Created by xin on 2017/6/10.
 */
public class InstanceRegistryTask extends SendAtFixedTimeTask<Instance> {

    private static final int DEFAULT_SLEEP_TIME = 5 * 1000;

    public InstanceRegistryTask(Sender<Instance> sender) {
        super(sender, DEFAULT_SLEEP_TIME);
    }

    @Override
    protected Instance sendData() {
        return new Instance(Config.Agent.APPLICATION_CODE);
    }

}
