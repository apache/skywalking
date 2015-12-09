package com.ai.cloud.skywalking.alarm.zk;

import com.ai.cloud.skywalking.alarm.AlarmServerRegisterWatcher;
import com.ai.cloud.skywalking.alarm.conf.Config;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class ZKUtil {

    private static Logger logger = LogManager.getLogger(ZKUtil.class);

    private static CuratorFramework client;

    static {
        try {
            RetryPolicy retryPolicy = new ExponentialBackoffRetry(Config.ZKPath.RETRY_TIMEOUT,
                    Config.ZKPath.RETRY_TIMES);
            CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder().
                    connectString(Config.ZKPath.CONNECT_STR)
                    .connectionTimeoutMs(Config.ZKPath.CONNECT_TIMEOUT).retryPolicy(retryPolicy);
            client = builder.build();
            client.start();

            createIfNotExists(getRegisterLockPath());
            createIfNotExists(getUserLockPathPrefix());
        } catch (Exception e) {
            logger.error("Failed to connect zookeeper.", e);
            System.exit(-1);
        }
    }

    public static InterProcessMutex getRegisterLock() {
        return new InterProcessMutex(client, getRegisterLockPath());
    }

    public static InterProcessMutex getProcessUserLock(String uid) {
        return new InterProcessMutex(client, getUserLockPath(uid));
    }

    public static void register(String serverId) {
        try {
            client.create().creatingParentsIfNeeded().forPath(getRegisterServerPathPrefix() + "/" + serverId);
        } catch (Exception e) {
            logger.error("Failed to register server", e);
        }
    }

    public static List<String> selectAllThreadIds() {
        try {
            return client.getChildren().forPath(getRegisterServerPathPrefix());
        } catch (Exception e) {
            logger.error("Failed to get children of Path[" + getRegisterServerPathPrefix() + "].", e);
        }
        return new ArrayList<String>();
    }

    public static void watch(AlarmServerRegisterWatcher instance) {
        try {
            client.getChildren().usingWatcher(instance).forPath(getRegisterServerPathPrefix());
        } catch (Exception e) {
            logger.error("Failed to get children for path[" + getRegisterServerPathPrefix() + "].", e);
        }
    }


    private static String getRegisterLockPath() {
        return Config.ZKPath.NODE_PREFIX + Config.ZKPath.SERVER_REGISTER_LOCK_PATH;
    }

    private static String getUserLockPath(String uid) {
        return getUserLockPathPrefix() + "/" + uid;
    }

    private static String getUserLockPathPrefix() {
        return Config.ZKPath.NODE_PREFIX + Config.ZKPath.USER_REGISTER_LOCK_PATH;
    }


    private static void createIfNotExists(String path) throws Exception {
        if (client.checkExists().forPath(path) == null) {
            client.create().creatingParentsIfNeeded().forPath(path);
        }
    }

    private static String getRegisterServerPathPrefix() {
        return Config.ZKPath.NODE_PREFIX + Config.ZKPath.REGISTER_SERVER_PATH;
    }
}
