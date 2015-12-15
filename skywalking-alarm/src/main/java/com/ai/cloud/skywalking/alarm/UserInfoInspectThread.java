package com.ai.cloud.skywalking.alarm;

import com.ai.cloud.skywalking.alarm.conf.Config;
import com.ai.cloud.skywalking.alarm.dao.AlarmMessageDao;
import com.ai.cloud.skywalking.alarm.util.ZKUtil;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

public class UserInfoInspectThread extends Thread {

    private boolean isInspector = false;
    private InterProcessMutex inspectorLock = new InterProcessMutex(ZKUtil.getZkClient(),
            Config.ZKPath.INSPECTOR_LOCK_PATH);
    private int userHashCode;
    private Logger logger = LogManager.getLogger(UserInfoInspectThread.class);

    public void run() {
        while (true) {
            try {
                // 探寻是否成为探测者
                if (!isInspector) {
                    while (!inspectorLock.acquire(5, TimeUnit.SECONDS)) {
                        Thread.sleep(Config.InspectThread.RETRY_GET_INSPECT_LOCK_INTERVAL);
                    }

                    isInspector = true;
                    userHashCode = AlarmMessageDao.selectAllUserIds().toString().hashCode();
                }

                // 判断上次用户数量是否发生变化
                if (!checkUserNumber()) {
                    Thread.sleep(Config.InspectThread.CHECK_USER_LIST_INTERVAL);
                    continue;
                }
                logger.info("The number of users has changed, activated redistribution task");
                // 激活重分配
                UserInfoCoordinator.activateRedistribute();
            } catch (Exception e) {
                logger.error("Failed to inspect number of user.", e);
            }
        }
    }

    private boolean checkUserNumber() throws SQLException {
        int currentUserHashCode = AlarmMessageDao.selectAllUserIds().toString().hashCode();
        if (userHashCode == currentUserHashCode) {
            return false;
        }
        userHashCode = currentUserHashCode;
        return true;
    }
}
