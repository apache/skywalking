package com.ai.cloud.skywalking.alarm;

import com.ai.cloud.skywalking.alarm.conf.Config;
import com.ai.cloud.skywalking.alarm.dao.AlarmMessageDao;
import com.ai.cloud.skywalking.alarm.model.ProcessThreadStatus;
import com.ai.cloud.skywalking.alarm.model.ProcessThreadValue;
import com.ai.cloud.skywalking.alarm.util.ProcessUtil;
import com.ai.cloud.skywalking.alarm.util.ZKUtil;
import com.google.gson.Gson;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UserInfoCoordinator extends Thread {


    private Logger logger = LogManager.getLogger(UserInfoInspector.class);

    private boolean redistributing;
    private boolean newServerComingFlag = false;

    public UserInfoCoordinator() {
        redistributing = false;
    }

    @Override
    public void run() {
        while (true) {
            //检查是否有新服务注册或者在重分配过程做有新处理线程启动了
            if (!redistributing || !newServerComingFlag) {
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException e) {
                    logger.error("Sleep error", e);
                }

                continue;
            }

            // 设置正在处理的标志位
            redistributing = true;
            newServerComingFlag = false;

            //获取当前所有的注册的处理线程
            List<String> registeredThreads = acquireAllRegisteredThread();
            //修改状态 (开始重新分配状态）
            changeStatus(registeredThreads, ProcessThreadStatus.REDISTRIBUTING);
            //检查所有的服务是否都处于空闲状态
            while (!isAllProcessThreadFree(registeredThreads)) {
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException e) {
                    logger.error("Sleep failed", e);
                }
            }

            //查询当前有多少用户
            List<String> users = AlarmMessageDao.selectAllUserIds();

            //将用户重新分配给服务
            allocationUser(registeredThreads, users);

            //修改状态(分配完成)
            changeStatus(registeredThreads, ProcessThreadStatus.REDISTRIBUTE_SUCCESS);
        }
    }

    private void allocationUser(List<String> registeredThreads, List<String> userIds) {
        Set<String> sortThreadIds = new HashSet<String>(registeredThreads);
        int step = (int) Math.ceil(userIds.size() * 1.0 / sortThreadIds.size());
        int start = 0;
        int end = step;

        if (end > userIds.size()) {
            end = userIds.size();
        }

        for (String thread : sortThreadIds) {
            String value = ZKUtil.getPathData(Config.ZKPath.REGISTER_SERVER_PATH + "/" + thread);
            ProcessThreadValue value1 = new Gson().fromJson(value, ProcessThreadValue.class);
            value1.setDealUserIds(userIds.subList(start, end));
            ZKUtil.setPathData(Config.ZKPath.REGISTER_SERVER_PATH + "/" + thread, new Gson().toJson(value1));

            start = end;
            end += step;

            if (end > userIds.size()) {
                break;
            }

        }
    }

    private List<String> acquireAllRegisteredThread() {
        return ZKUtil.getChildren(Config.ZKPath.REGISTER_SERVER_PATH);
    }

    private boolean isAllProcessThreadFree(List<String> registeredThreadIds) {
        String registerPathPrefix = Config.ZKPath.REGISTER_SERVER_PATH + "/";
        for (String threadId : registeredThreadIds) {
            if (getProcessThreadStatus(registerPathPrefix, threadId)
                    != ProcessThreadStatus.FREE) {
                return false;
            }
        }
        return true;
    }

    private ProcessThreadStatus getProcessThreadStatus(String registerPathPrefix, String threadId) {
        String value = ZKUtil.getPathData(registerPathPrefix + threadId);
        ProcessThreadValue value1 = new Gson().fromJson(value, ProcessThreadValue.class);
        return ProcessThreadStatus.convert(value1.getStatus());
    }


    private void changeStatus(List<String> registeredThreadIds, ProcessThreadStatus status) {
        for (String threadId : registeredThreadIds) {
            ProcessUtil.changeProcessThreadStatus(threadId, status);
        }
    }

    public class RegisterServerWatcher implements CuratorWatcher {

        @Override
        public void process(WatchedEvent watchedEvent) throws Exception {
            if (watchedEvent.getType() == Watcher.Event.EventType.NodeChildrenChanged) {
                if (redistributing) {
                    newServerComingFlag = true;
                } else {
                    redistributing = true;
                }
            }
        }
    }
}
