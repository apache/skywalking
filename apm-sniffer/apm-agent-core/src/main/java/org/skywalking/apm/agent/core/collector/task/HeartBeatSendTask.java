package org.skywalking.apm.agent.core.collector.task;

import org.skywalking.apm.agent.core.collector.Sender;
import org.skywalking.apm.agent.core.collector.entity.HeartBeatInfo;
import org.skywalking.apm.agent.core.conf.Config;

public class HeartBeatSendTask extends SendAtFixedTimeTask<HeartBeatInfo> {

    public static final int SLEEP_TIME = 1000;

    public HeartBeatSendTask(Sender<HeartBeatInfo> sender) {
        super(sender, SLEEP_TIME);
    }

    @Override
    protected HeartBeatInfo sendData() {
        if (!registrySuccess(Config.Agent.INSTANCE_ID)) {
            return null;
        }

        return new HeartBeatInfo(Config.Agent.APPLICATION_CODE);
    }

    private boolean registrySuccess(int instanceId) {
        return instanceId >= 0;
    }
}
