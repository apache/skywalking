package com.ai.cloud.skywalking.alarm;

import com.ai.cloud.skywalking.alarm.dao.AlarmMessageDao;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class UserInfoInspector extends Thread {

    private Logger logger = LogManager.getLogger(UserInfoInspector.class);

    private int preUserSize;

    public UserInfoInspector() {
        preUserSize = AlarmMessageDao.selectUserCount();
    }

    @Override
    public void run() {
        int currentUserSize;
        while (true) {
            try {
                Thread.sleep(10 * 1000L);
            } catch (InterruptedException e) {
                logger.error("Sleep Failed", e);
            }

            currentUserSize = AlarmMessageDao.selectUserCount();

            if (currentUserSize != preUserSize) {
                logger.info("Total user has been changed. Notice all process thread to change process date.");
                for (AlarmMessageProcessThread thread : AlarmProcessServer.getProcessThreads()) {
                    thread.setChanged(true);
                }
            }
        }
    }
}
