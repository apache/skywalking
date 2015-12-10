package com.ai.cloud.skywalking.alarm.util;

import com.ai.cloud.skywalking.alarm.conf.Config;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.CuratorWatcher;
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


    public static String getPathData(String path) {
        try {
            return new String(client.getData().forPath(path));
        } catch (Exception e) {
            logger.error("Failed to get the value of path[{}]", path, e);
        }
        return "";
    }

    public static String getPathDataWithWatch(String path, CuratorWatcher watcher) {
        try {
            return new String(client.getData().usingWatcher(watcher).forPath(path));
        } catch (Exception e) {
            logger.error("Failed to get the value of path[{}]", path, e);
        }
        return "";
    }

    public static void setPathData(String path, String value) {
        try {
            client.setData().forPath(path, value.getBytes());
        } catch (Exception e) {
            logger.error("Failed to set date of path[{{}]", path, e);
        }
    }

    public static List<String> getChildren(String registerServerPath) {
        try {
            return client.getChildren().forPath(registerServerPath);
        } catch (Exception e) {
            logger.error("Failed to get child nodes of path[{{}]", registerServerPath, e);
        }
        return new ArrayList<String>();
    }
}
