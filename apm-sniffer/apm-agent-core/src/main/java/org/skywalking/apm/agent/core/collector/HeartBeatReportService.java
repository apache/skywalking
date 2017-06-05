package org.skywalking.apm.agent.core.collector;

import org.skywalking.apm.agent.core.boot.BootService;
import org.skywalking.apm.agent.core.collector.sender.HeartBeatSender;
import org.skywalking.apm.agent.core.collector.task.HeartBeatSendTask;

public class HeartBeatReportService implements BootService {

    @Override
    public void bootUp() throws Throwable {
        new HeartBeatSendTask(new HeartBeatSender()).start();
    }
}
