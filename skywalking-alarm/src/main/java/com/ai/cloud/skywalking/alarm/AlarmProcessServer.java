package com.ai.cloud.skywalking.alarm;

import com.ai.cloud.skywalking.alarm.conf.Config;
import com.ai.cloud.skywalking.alarm.conf.ConfigInitializer;
import com.ai.cloud.skywalking.alarm.util.ZKUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class AlarmProcessServer {

    private static Logger logger = LogManager.getLogger(AlarmProcessServer.class);

    private static List<AlarmMessageProcessThread> processThreads =
            new ArrayList<AlarmMessageProcessThread>();

    public static void main(String[] main) throws IOException, IllegalAccessException {
        logger.info("Begin to start alarm process server....");

        logger.info("Begin to initialize configuration");
        initializeParam();
        logger.info("Finished to initialize configuration");

        if (!ZKUtil.exists(Config.ZKPath.REGISTER_SERVER_PATH)) {
            ZKUtil.createPath(Config.ZKPath.REGISTER_SERVER_PATH);
        }
        new UserInfoCoordinator().start();

        logger.info("Begin to start process thread...");
        AlarmMessageProcessThread tmpThread;
        for (int i = 0; i < Config.Server.PROCESS_THREAD_SIZE; i++) {
            tmpThread = new AlarmMessageProcessThread();
            tmpThread.start();
            processThreads.add(tmpThread);
        }
        logger.info("Successfully launched {} processing threads.", Config.Server.PROCESS_THREAD_SIZE);
        new UserNumberInspectThread().start();
        logger.info("Successfully launched the thread that inspect the number of user");
        logger.info("Alarm process server successfully started.");
        while (true) {
            try {
                Thread.sleep(Config.Server.DAEMON_THREAD_WAIT_INTERVAL);
            } catch (InterruptedException e) {
                logger.error("Sleep failed", e);
            }
        }
    }

    private static void initializeParam() throws IllegalAccessException, IOException {
        Properties properties = new Properties();
        try {
            properties.load(AlarmProcessServer.class.getResourceAsStream("/config.properties"));
            ConfigInitializer.initialize(properties, Config.class);
        } catch (IllegalAccessException e) {
            logger.error("Initialize the collect server configuration failed", e);
            throw e;
        } catch (IOException e) {
            logger.error("Initialize the collect server configuration failed", e);
            throw e;
        }
    }
}
