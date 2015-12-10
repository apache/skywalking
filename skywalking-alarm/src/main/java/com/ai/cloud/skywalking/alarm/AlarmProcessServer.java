package com.ai.cloud.skywalking.alarm;

import com.ai.cloud.skywalking.alarm.conf.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class AlarmProcessServer {

    private static Logger logger = LogManager.getLogger(AlarmProcessServer.class);

    private static List<AlarmMessageProcessThread> processThreads =
            new ArrayList<AlarmMessageProcessThread>();

    public static void main(String[] main) {
        logger.info("Begin to start alarm process server....");
        logger.info("Begin to start process thread...");
        AlarmMessageProcessThread tmpThread;
        for (int i = 0; i < Config.Server.PROCESS_THREAD_SIZE; i++) {
            tmpThread = new AlarmMessageProcessThread();
            tmpThread.start();
            processThreads.add(tmpThread);
        }
        logger.info("Successfully launched {} processing threads.", Config.Server.PROCESS_THREAD_SIZE);
        logger.info("Begin to start user inspector thread....");
        new UserInfoInspector().start();
        logger.info("Start user inspector thread success....");
        logger.info("Alarm process server successfully started.");
        while (true) {
            try {
                Thread.sleep(Config.Server.DAEMON_THREAD_WAIT_INTERVAL);
            } catch (InterruptedException e) {
                logger.error("Sleep failed", e);
            }
        }
    }


    public static List<AlarmMessageProcessThread> getProcessThreads() {
        return processThreads;
    }
}
