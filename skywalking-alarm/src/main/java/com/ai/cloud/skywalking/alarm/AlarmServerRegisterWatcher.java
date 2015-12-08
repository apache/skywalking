package com.ai.cloud.skywalking.alarm;

import com.ai.cloud.skywalking.alarm.zk.ZKUtil;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;

public class AlarmServerRegisterWatcher implements CuratorWatcher {

    private static AlarmServerRegisterWatcher watcher = new AlarmServerRegisterWatcher();

    @Override
    public void process(WatchedEvent watchedEvent) throws Exception {
        if (watchedEvent.getType() == Watcher.Event.EventType.NodeChildrenChanged) {
            for (AlarmMessageProcessThread thread : AlarmProcessServer.getProcessThreads()) {
                thread.setChanged(true);
            }
        }

        ZKUtil.watch(getInstance());
    }

    public static AlarmServerRegisterWatcher getInstance() {
        return watcher;
    }
}
