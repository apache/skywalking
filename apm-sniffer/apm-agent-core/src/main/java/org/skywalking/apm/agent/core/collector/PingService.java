package org.skywalking.apm.agent.core.collector;

import org.skywalking.apm.agent.core.boot.BootService;
import org.skywalking.apm.agent.core.collector.sender.HeartBeatSender;
import org.skywalking.apm.agent.core.collector.task.PingSendTask;

public class PingService implements BootService {

    @Override
    public void bootUp() throws Throwable {
        new PingSendTask(new HeartBeatSender()).start();
    }
}
