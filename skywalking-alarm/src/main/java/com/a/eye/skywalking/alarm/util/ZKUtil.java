package com.a.eye.skywalking.alarm.util;

import com.a.eye.skywalking.alarm.conf.Config;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.zookeeper.CreateMode;

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
        } catch (Exception e) {
            logger.error("Failed to connect zookeeper.", e);
            System.exit(-1);
        }
    }

    public static CuratorFramework getZkClient() {
        return client;
    }

    public static InterProcessMutex getLock(String path) {
        return new InterProcessMutex(client, path);
    }


    public static String getPathData(String path) throws Exception {
        return new String(client.getData().forPath(path));
    }

    public static String getPathDataWithWatch(String path, CuratorWatcher watcher) throws Exception {
        return new String(client.getData().usingWatcher(watcher).forPath(path));
    }

    public static void setPathData(String path, String value) throws Exception {
        client.setData().forPath(path, value.getBytes());
    }

    public static List<String> getChildren(String registerServerPath) throws Exception {
        return client.getChildren().forPath(registerServerPath);
    }

    public static List<String> getChildrenWithWatcher(String registerServerPath, CuratorWatcher watcher) throws Exception {
        return client.getChildren().usingWatcher(watcher).forPath(registerServerPath);
    }

    public static void createPath(String path) {
        try {
            client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(path);
        } catch (Exception e) {
            logger.error("Failed to create path.");
        }
    }

    public static boolean exists(String registerServerPath) {
        try {
            return client.checkExists().forPath(registerServerPath) != null;
        } catch (Exception e) {
            logger.error("Failed check exists for path");
        }

        return false;
    }
}
