package org.skywalking.apm.agent.core.collector.task;

import org.skywalking.apm.agent.core.collector.Sender;
import org.skywalking.apm.agent.core.collector.entity.InstanceInfo;
import org.skywalking.apm.agent.core.conf.Config;

/**
 * Created by xin on 2017/6/10.
 */
public class InstanceRegistryTask extends SendAtFixedTimeTask<InstanceInfo> {

    private static final int DEFAULT_SLEEP_TIME = 5 * 1000;

    public InstanceRegistryTask(Sender<InstanceInfo> sender) {
        super(sender, DEFAULT_SLEEP_TIME);
    }

    @Override
    protected InstanceInfo sendData() {
        return new InstanceInfo(Config.Agent.APPLICATION_CODE);
    }

}
