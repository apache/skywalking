package com.a.eye.skywalking.analysis.mapper.util;

import com.a.eye.skywalking.analysis.config.ConfigInitializer;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.*;

import java.io.IOException;

/**
 * Created by xin on 16-5-4.
 */
public class HBaseUtils {

    private static String ZK_QUORUM = "host-10-1-235-197,host-10-1-235-198,host-10-1-235-199";
    private static String ZK_CLIENT_PORT = "29181";

    private static Configuration configuration = null;
    private static Connection connection;

    static {
        if (configuration == null) {
            configuration = HBaseConfiguration.create();
            configuration.set("hbase.zookeeper.quorum", ZK_QUORUM);
            configuration.set("hbase.zookeeper.property.clientPort", ZK_CLIENT_PORT);
            configuration.set("hbase.rpc.timeout", "600000");
            try {
                connection = ConnectionFactory.createConnection(configuration);
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }

        ConfigInitializer.initialize();
    }

    public static Connection getConnection() {
        return connection;
    }
}
