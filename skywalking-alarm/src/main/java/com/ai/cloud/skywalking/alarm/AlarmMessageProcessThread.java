package com.ai.cloud.skywalking.alarm;

import com.ai.cloud.skywalking.alarm.conf.Config;
import com.ai.cloud.skywalking.alarm.dao.AlarmMessageDao;
import com.ai.cloud.skywalking.alarm.model.AlarmRule;
import com.ai.cloud.skywalking.alarm.model.ProcessThreadStatus;
import com.ai.cloud.skywalking.alarm.model.UserInfo;
import com.ai.cloud.skywalking.alarm.procesor.AlarmMessageProcessor;
import com.ai.cloud.skywalking.alarm.util.ProcessUtil;
import com.ai.cloud.skywalking.alarm.util.ZKUtil;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AlarmMessageProcessThread extends Thread {

    private Logger logger = LogManager.getLogger(AlarmMessageProcessThread.class);

    private String threadId;
    private ProcessThreadStatus status;
    private String[] processUserIds;
    private List<InterProcessMutex> usersLocks;
    private CoordinatorStatusWatcher watcher = new CoordinatorStatusWatcher();

    public AlarmMessageProcessThread() {
        // 初始化生成ThreadId
        threadId = UUID.randomUUID().toString();
    }

    @Override
    public void run() {
        //注册服务(默认为空闲状态)
        registerProcessThread(threadId, ProcessThreadStatus.FREE);
        while (true) {
            //检查是否为忙碌状态
            if (status == ProcessThreadStatus.BUSY) {
                //处理告警信息
                for (String userId : processUserIds) {
                    List<AlarmRule> rules = AlarmMessageDao.selectAlarmRulesByUserId(userId);
                    UserInfo userInfo = AlarmMessageDao.selectUser(userId);
                    for (AlarmRule rule : rules) {
                        new AlarmMessageProcessor().process(userInfo, rule);
                    }
                }
            }

            //检查是否分配线程的状态(重新分配状态)
            if (status == ProcessThreadStatus.REDISTRIBUTING) {
                // 释放用户锁
                releaseUserLock();
                // 修改自身状态：(空闲状态)
                status = ProcessThreadStatus.FREE;
                ProcessUtil.changeProcessThreadStatus(threadId, ProcessThreadStatus.FREE);
            }

            //检查分配线程的状态(分配完成状态)
            if (status == ProcessThreadStatus.REDISTRIBUTE_SUCCESS) {
                // 获取待处理的用户
                processUserIds = acquireProcessedUsers();
                // 给用户加锁
                lockUser(processUserIds);
                // 修改自身状态 ：(忙碌状态)
                status = ProcessThreadStatus.BUSY;
                ProcessUtil.changeProcessThreadStatus(threadId, ProcessThreadStatus.BUSY);
            }

            try {
                Thread.sleep(Config.ProcessThread.THREAD_WAIT_INTERVAL);
            } catch (InterruptedException e) {
                logger.error("Sleep failed.", e);
            }
        }
    }

    private String[] acquireProcessedUsers() {
        String path = Config.ZKPath.REGISTER_SERVER_PATH + "/" + threadId;
        String value = ZKUtil.getPathData(path);
        String[] valueArrays = value.split("@");
        String toBeProcessUserId;
        if (valueArrays.length < 2) {
            toBeProcessUserId = "";
        } else {
            toBeProcessUserId = valueArrays[1];
        }

        return toBeProcessUserId.split(";");
    }

    private void lockUser(String[] userIds) {
        usersLocks = new ArrayList<InterProcessMutex>();
        String userLockPath = Config.ZKPath.USER_REGISTER_LOCK_PATH + "/";
        InterProcessMutex tmpLock;
        for (String userId : userIds) {
            tmpLock = ZKUtil.getLock(userLockPath + userId);
            try {
                tmpLock.acquire();
            } catch (Exception e) {
                logger.error("Failed to lock user[{}]", userId, e);
                //TODO 锁失败，该怎么处理？
            }
            usersLocks.add(tmpLock);
        }
    }

    private void releaseUserLock() {
        for (InterProcessMutex lock : usersLocks) {
            try {
                lock.release();
            } catch (Exception e) {
                //
                logger.error("Failed to release lock user.", e);
                //TODO 释放锁，该怎么处理。
            }
        }

        usersLocks.clear();
    }

    private void registerProcessThread(String threadId, ProcessThreadStatus status) {
        try {
            String registerPath = Config.ZKPath.REGISTER_SERVER_PATH + "/" + threadId;
            String registerValue = status + "@ ";
            ZKUtil.getZkClient().create().creatingParentsIfNeeded()
                    .forPath(registerPath, registerValue.getBytes());

            this.status = status;


            ZKUtil.getPathDataWithWatch(Config.ZKPath.REGISTER_SERVER_PATH + "/" + threadId, watcher);
        } catch (Exception e) {
            logger.error("Failed to register process thread.", e);
        }
    }

    private class CoordinatorStatusWatcher implements CuratorWatcher {

        @Override
        public void process(WatchedEvent watchedEvent) {
            if (watchedEvent.getType() == Watcher.Event.EventType.NodeDataChanged) {
                String value = ZKUtil.getPathData(Config.ZKPath.COORDINATOR_STATUS_PATH);
                status = ProcessThreadStatus.convert(value);
            }

            ZKUtil.getPathDataWithWatch(Config.ZKPath.REGISTER_SERVER_PATH + "/" + threadId, watcher);
        }
    }


}
