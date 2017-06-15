package org.skywalking.apm.agent.core.collector.task;

import org.skywalking.apm.agent.core.collector.Sender;
import org.skywalking.apm.agent.core.collector.entity.PingInfo;

import static org.skywalking.apm.agent.core.conf.Config.Agent.INSTANCE_ID;

public class PingSendTask extends SendAtFixedTimeTask<PingInfo> {

    public static final int SLEEP_TIME = 1000;

    public PingSendTask(Sender<PingInfo> sender) {
        super(sender, SLEEP_TIME);
    }

    @Override
    protected PingInfo sendData() {
        if (!registrySuccess(INSTANCE_ID)) {
            return null;
        }

        return new PingInfo(INSTANCE_ID);
    }

    private boolean registrySuccess(int instanceId) {
        return instanceId >= 0;
    }
}
