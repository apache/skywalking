package com.ai.cloud.skywalking.alarm;

import com.ai.cloud.skywalking.alarm.dao.AlarmMessageDao;
import com.ai.cloud.skywalking.alarm.model.ApplicationInfo;
import com.ai.cloud.skywalking.alarm.model.UserInfo;
import com.ai.cloud.skywalking.alarm.zk.ZKUtil;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class AlarmMessageProcessThread extends Thread {
    private int rank;
    private String threadId;
    private Logger logger = LogManager.getLogger(AlarmMessageProcessThread.class);
    private boolean isChanged = false;
    private List<InterProcessMutex> locks;
    private List<UserInfo> toBeProcessUsers;

    public AlarmMessageProcessThread() {
        threadId = UUID.randomUUID().toString();
        registerServer(threadId);
        rank = ballot(threadId);
        ZKUtil.watch(AlarmServerRegisterWatcher.getInstance());
        toBeProcessUsers = getToBeProcessUsers();
    }


    @Override
    public void run() {
        lockAllUsers();
        while (true) {
            for (UserInfo userInfo : toBeProcessUsers) {
                for (ApplicationInfo applicationInfo : userInfo.getApplicationInfos()) {
                    System.out.println(threadId + applicationInfo.getAppId());
                }
            }

            if (isChanged) {
                unlockAllUsers();
                rank = ballot(threadId);
                toBeProcessUsers = getToBeProcessUsers();
                lockAllUsers();
                isChanged = false;
            }

            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                logger.error("Sleep failed", e);
            }
        }
    }

    private void unlockAllUsers() {
        for (InterProcessMutex lock : locks) {
            try {
                lock.release();
            } catch (Exception e) {
                logger.error("Failed to release lock[{}]", e);
            }
        }
    }

    private void lockAllUsers() {
        locks = new ArrayList<InterProcessMutex>(toBeProcessUsers.size());
        InterProcessMutex tmpLock;
        for (UserInfo userInfo : toBeProcessUsers) {
            tmpLock = ZKUtil.getProcessUserLock(userInfo.getUserId());
            locks.add(tmpLock);
            try {
                tmpLock.acquire();
            } catch (Exception e) {
                logger.error("Failed to lock ");
            }

        }
    }

    private List<UserInfo> getToBeProcessUsers() {
        List<UserInfo> userInfos = AlarmMessageDao.selectAllUserInfo();
        List<String> allServerIds = ZKUtil.selectAllServerIds();
        int step = (int) Math.ceil(userInfos.size() * 1.0 / allServerIds.size());
        int start = rank * step;
        int end = (rank + 1) * step;
        if (end > userInfos.size()) {
            return new ArrayList<UserInfo>();
        }

        List<UserInfo> toBeProcessUsers = userInfos.subList(start, end);
        for (UserInfo userInfo : toBeProcessUsers) {
            userInfo.setApplicationInfos(AlarmMessageDao.selectAllApplicationsByUserId(userInfo.getUserId()));
        }
        return toBeProcessUsers;
    }


    private void registerServer(String serverId) {
        InterProcessMutex registerLock = ZKUtil.getRegisterLock();
        try {
            registerLock.acquire();
            ZKUtil.register(serverId);
        } catch (Exception e) {
            logger.error("Failed to lock.", e);
        } finally {
            if (registerLock != null) {
                try {
                    registerLock.release();
                } catch (Exception e) {
                    logger.error("Failed to release lock.", e);
                }
            }
        }
    }

    private int ballot(String threadId) {
        List<String> serverIds = ZKUtil.selectAllServerIds();
        int rank = 0;
        for (String tmpServerId : serverIds) {
            if (tmpServerId.hashCode() < threadId.hashCode()) {
                rank++;
            }
        }
        return rank;
    }


    public void setChanged(boolean changed) {
        isChanged = changed;
    }
}
